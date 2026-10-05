package mx.dev1.naturequest.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.dev1.naturequest.data.image.ImageDownscaler
import mx.dev1.naturequest.data.inference.EngineOptions
import mx.dev1.naturequest.data.inference.LiteRtInferenceEngine
import mx.dev1.naturequest.data.model.ModelStore
import mx.dev1.naturequest.data.prompts.PromptRepository
import mx.dev1.naturequest.domain.inference.InferenceRequest
import mx.dev1.naturequest.domain.inference.InferenceStats
import mx.dev1.naturequest.domain.inference.ModelJsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs the model spike: load time, hunt generation, and photo verification against /samples.
 * Debug builds only. Results go to the on-screen log, logcat (tag NQ_SPIKE) and a JSON report in
 * the app's files dir.
 */
class SpikeRunner(
    private val context: Context,
    private val log: (String) -> Unit,
) {
    private val modelStore = ModelStore(context)
    private val prompts = PromptRepository(context)
    private val parser = ModelJsonParser()
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun run(config: SpikeConfig): SpikeReport = withContext(Dispatchers.Default) {
        val modelFile = modelStore.modelFile()
        var report = SpikeReport(
            startedAt = Instant.now().toString(),
            device = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})",
            modelFile = modelFile.name,
            modelBytes = if (modelFile.isFile) modelFile.length() else 0,
            backend = config.backend.name,
            visionBackend = (config.visionBackend ?: config.backend).name,
            maxImageSidePx = config.maxImageSidePx,
            visualTokenBudget = config.visualTokenBudget,
            maxNumTokens = config.maxNumTokens,
        )
        say("Spike: ${report.device}")
        say("Config: backend=${config.backend} vision=${config.visionBackend ?: config.backend} maxSide=${config.maxImageSidePx}px visualTokens=${config.visualTokenBudget} maxTokens=${config.maxNumTokens}")
        if (!modelStore.isAvailable()) {
            say("MODEL MISSING at $modelFile. Push it first (see CLAUDE.md).")
            return@withContext report.copy(loadError = "Model file missing").also(::save)
        }

        val memory = mutableListOf(readMemory("before load"))
        val engine = LiteRtInferenceEngine(
            context = context,
            modelStore = modelStore,
            options = EngineOptions(config.backend, config.visionBackend, config.visualTokenBudget, config.maxNumTokens),
        )
        try {
            say("Loading model...")
            val firstLoadMs = timed { engine.load() }
            memory += readMemory("after load")
            say("Model loaded in ${firstLoadMs} ms")
            report = report.copy(firstLoadMs = firstLoadMs)

            val generation = runGeneration(engine, config)
            memory += readMemory("after generation")

            val verification = runVerification(engine, config)
            // No real photos yet: still prove the vision path works and measure its latency.
            val synthetic = if (verification.isEmpty()) runSyntheticVerification(engine, config) else emptyList()
            memory += readMemory("after verification")

            engine.release()
            var secondLoadMs: Long? = null
            if (config.measureSecondLoad) {
                say("Reloading to measure the cached load...")
                secondLoadMs = timed { engine.load() }
                say("Second load: $secondLoadMs ms")
            }
            report = report.copy(
                secondLoadMs = secondLoadMs,
                memory = memory,
                generation = generation,
                verification = verification,
                synthetic = synthetic,
                summary = summarize(generation, verification),
            )
        } catch (e: CancellationException) {
            say("Cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Spike failed", e)
            say("FAILED: ${e::class.simpleName}: ${e.message}")
            report = report.copy(loadError = report.loadError ?: "${e::class.simpleName}: ${e.message}", memory = memory)
        } finally {
            engine.release()
        }
        memory.add(readMemory("after release"))
        report = report.copy(memory = memory)
        save(report)
        report.summary?.let { say("SUMMARY $it") }
        report
    }

    private suspend fun runGeneration(engine: LiteRtInferenceEngine, config: SpikeConfig): List<GenerationRun> {
        val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val runs = mutableListOf<GenerationRun>()
        for (case in GENERATION_CASES.take(config.generationCases)) {
            currentCoroutineContext().ensureActive()
            say("Generating: ${case.place}, ${case.count} items, age ${case.ageRange}, ${case.language}")
            val prompt = prompts.render(
                "hunt_generation_v1",
                mapOf(
                    "count" to case.count.toString(),
                    "place" to case.place,
                    "month" to month,
                    "age_range" to case.ageRange,
                    "language" to case.language,
                ),
            )
            val run = try {
                val result = engine.generate(InferenceRequest(prompt, temperature = 0.8, maxOutputTokens = 400))
                val parsed = parser.parseHuntList(result.text)
                val items = parsed.getOrNull()?.items.orEmpty()
                say("  -> ${result.stats.totalMs} ms (first token ${result.stats.timeToFirstTokenMs} ms, ${result.stats.decodeTokens} tokens), parsed=${parsed.isSuccess}, items=${items.size}")
                items.forEach { say("     - $it") }
                GenerationRun(
                    place = case.place, count = case.count, ageRange = case.ageRange, language = case.language,
                    parsedOk = parsed.isSuccess, itemCount = items.size, countMatches = items.size == case.count,
                    flaggedItems = items.filter { RISKY_WORDS.containsMatchIn(it) },
                    rawText = result.text, stats = result.stats,
                    error = parsed.exceptionOrNull()?.message,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("  -> ERROR ${e::class.simpleName}: ${e.message}")
                GenerationRun(case.place, case.count, case.ageRange, case.language, false, 0, false, emptyList(), "", null, e.message)
            }
            runs += run
        }
        return runs
    }

    private suspend fun runVerification(engine: LiteRtInferenceEngine, config: SpikeConfig): List<VerificationRun> {
        val samples = context.assets.list("").orEmpty()
            .filter { name -> IMAGE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } }
            .sorted()
        if (samples.isEmpty()) {
            say("No photos in /samples: skipping verification (add photos and rebuild).")
            return emptyList()
        }
        val labels = samples.map(::sampleLabel)
        val distinctLabels = labels.distinct().sorted()
        val runs = mutableListOf<VerificationRun>()
        samples.forEachIndexed { index, sample ->
            val label = labels[index]
            val cases = buildList {
                add(label to true)
                if (config.includeNegatives && distinctLabels.size > 1) {
                    // A different label from the sample set: the model must say it is not a match.
                    add(distinctLabels[(distinctLabels.indexOf(label) + 1) % distinctLabels.size] to false)
                }
            }
            val original = context.assets.open(sample).use { it.readBytes() }
            val prepStart = SystemClock.elapsedRealtime()
            val jpeg = ImageDownscaler.toJpeg(original, config.maxImageSidePx)
            val prepMs = SystemClock.elapsedRealtime() - prepStart
            for ((item, expected) in cases) {
                currentCoroutineContext().ensureActive()
                val prompt = prompts.render("verification_v1", mapOf("item" to item, "language" to "English"))
                val run = try {
                    val result = engine.generate(
                        InferenceRequest(prompt, imageJpeg = jpeg, temperature = 0.2, maxOutputTokens = 120),
                    )
                    val parsed = parser.parseVerification(result.text)
                    val response = parsed.getOrNull()
                    val correct = response != null && response.match == expected
                    say("  $sample vs \"$item\" (expect ${if (expected) "match" else "no match"}): ${if (correct) "OK" else "WRONG"} in ${result.stats.totalMs} ms (prep ${prepMs} ms, ${original.size / 1024}KB -> ${jpeg.size / 1024}KB) match=${response?.match}")
                    VerificationRun(
                        sample = sample, item = item, expectedMatch = expected, parsedOk = parsed.isSuccess,
                        match = response?.match, correct = correct, message = response?.message, hint = response?.hint,
                        rawText = result.text, prepMs = prepMs, stats = result.stats,
                        error = parsed.exceptionOrNull()?.message,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    say("  $sample vs \"$item\": ERROR ${e::class.simpleName}: ${e.message}")
                    VerificationRun(sample, item, expected, false, null, false, null, null, "", prepMs, null, e.message)
                }
                runs += run
            }
        }
        return runs
    }

    /** Latency check on a drawn red "leaf": proves image input works. Results are not accuracy data. */
    private suspend fun runSyntheticVerification(engine: LiteRtInferenceEngine, config: SpikeConfig): List<VerificationRun> {
        say("Synthetic image (timing only, NOT scored): 3 runs")
        val original = syntheticPhoto()
        val prepStart = SystemClock.elapsedRealtime()
        val jpeg = ImageDownscaler.toJpeg(original, config.maxImageSidePx)
        val prepMs = SystemClock.elapsedRealtime() - prepStart
        val item = "a red leaf"
        val prompt = prompts.render("verification_v1", mapOf("item" to item, "language" to "English"))
        return (1..3).map { n ->
            currentCoroutineContext().ensureActive()
            try {
                val result = engine.generate(InferenceRequest(prompt, imageJpeg = jpeg, temperature = 0.2, maxOutputTokens = 120))
                val parsed = parser.parseVerification(result.text)
                say("  run $n: ${result.stats.totalMs} ms (first token ${result.stats.timeToFirstTokenMs} ms, prefill ${result.stats.prefillTokens} tok, decode ${result.stats.decodeTokens} tok), parsed=${parsed.isSuccess}")
                say("     prefill ${"%.0f".format(result.stats.prefillTokensPerSecond ?: 0.0)} tok/s, decode ${"%.1f".format(result.stats.decodeTokensPerSecond ?: 0.0)} tok/s")
                say("     raw: ${result.text.take(300)}")
                VerificationRun(
                    sample = "synthetic.jpg", item = item, expectedMatch = true, parsedOk = parsed.isSuccess,
                    match = parsed.getOrNull()?.match, correct = false, message = parsed.getOrNull()?.message,
                    hint = parsed.getOrNull()?.hint, rawText = result.text, prepMs = prepMs, stats = result.stats,
                    error = parsed.exceptionOrNull()?.message,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("  run $n: ERROR ${e::class.simpleName}: ${e.message}")
                VerificationRun("synthetic.jpg", item, true, false, null, false, null, null, "", prepMs, null, e.message)
            }
        }
    }

    private fun syntheticPhoto(): ByteArray {
        val bitmap = createBitmap(4000, 3000) // phone-photo sized
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF3B7D3A.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.rotate(-25f, 2000f, 1500f)
        paint.color = 0xFFC62828.toInt()
        canvas.drawOval(RectF(1000f, 900f, 3000f, 2100f), paint)
        paint.color = 0xFF5D4037.toInt()
        paint.strokeWidth = 30f
        canvas.drawLine(1050f, 1500f, 2950f, 1500f, paint)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun summarize(generation: List<GenerationRun>, verification: List<VerificationRun>): SpikeSummary {
        val genMs = generation.mapNotNull { it.stats?.totalMs }
        val verMs = verification.mapNotNull { it.stats?.totalMs }
        val positives = verification.filter { it.expectedMatch }
        val negatives = verification.filterNot { it.expectedMatch }
        return SpikeSummary(
            generationMsAvg = genMs.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            generationMsMax = genMs.maxOrNull(),
            generationParsedOk = "${generation.count { it.parsedOk }}/${generation.size}",
            verificationMsAvg = verMs.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            verificationMsMax = verMs.maxOrNull(),
            positivesCorrect = "${positives.count { it.correct }}/${positives.size}",
            negativesCorrect = "${negatives.count { it.correct }}/${negatives.size}",
            verificationParsedOk = "${verification.count { it.parsedOk }}/${verification.size}",
        )
    }

    private fun save(report: SpikeReport) {
        val name = "spike_report_${report.backend.lowercase()}-v${report.visionBackend.lowercase()}_b${report.visualTokenBudget}_s${report.maxImageSidePx}.json"
        val file = File(context.filesDir, name)
        file.writeText(json.encodeToString(report))
        say("Report saved: files/$name")
    }

    private fun readMemory(label: String): MemoryPoint {
        val status = File("/proc/self/status").readLines()
        fun kb(key: String) = status.firstOrNull { it.startsWith("$key:") }
            ?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
        val pss = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss.toLong()
        return MemoryPoint(label, kb("VmHWM") / 1024, kb("VmRSS") / 1024, pss / 1024).also {
            say("Memory [$label]: peak RSS ${it.vmHwmMb} MB, RSS ${it.vmRssMb} MB, PSS ${it.pssMb} MB")
        }
    }

    private suspend fun timed(block: suspend () -> Unit): Long {
        val start = SystemClock.elapsedRealtime()
        block()
        return SystemClock.elapsedRealtime() - start
    }

    private fun say(line: String) {
        Log.i(TAG, line)
        log(line)
    }

    private class GenerationCase(val place: String, val count: Int, val ageRange: String, val language: String)

    companion object {
        const val TAG = "NQ_SPIKE"
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")
        private val GENERATION_CASES = listOf(
            GenerationCase("urban walk", 5, "4-6", "English"),
            GenerationCase("park", 8, "7-9", "English"),
            GenerationCase("forest", 12, "10 and older", "English"),
            GenerationCase("garden", 8, "7-9", "Spanish"),
        )
        // Rough screen for risky generated items; the real validator comes in the generation step.
        private val RISKY_WORDS = Regex(
            "\\b(pick|touch|eat|taste|catch|climb|swim|lake|river|pond|water|road|street|cliff|collect|take|feed|" +
                "toca|come|prueba|agua|río|rio|lago|carretera|calle|escal|recoge|corta)\\b",
            RegexOption.IGNORE_CASE,
        )

        /** "red_leaf_2.jpg" -> "red leaf"; "feather1.png" -> "feather". */
        fun sampleLabel(fileName: String): String =
            fileName.substringBeforeLast('.')
                .replace(Regex("[_\\- ]*\\d+$"), "")
                .replace('_', ' ').replace('-', ' ')
                .trim()
                .lowercase()
    }
}
