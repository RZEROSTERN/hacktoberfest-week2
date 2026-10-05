package mx.dev1.naturequest.domain.hunt

import mx.dev1.naturequest.domain.inference.InferenceEngine
import mx.dev1.naturequest.domain.inference.InferenceRequest
import mx.dev1.naturequest.domain.inference.ModelJsonParser
import mx.dev1.naturequest.domain.prompt.PromptSource
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** Safe, curated items in the device language, used when the model cannot produce enough valid ones. */
fun interface FallbackItems {
    fun items(): List<String>
}

enum class HuntGenerationProgress { LOADING_MODEL, WRITING_LIST }

class RejectedItem(val text: String, val reason: SafetyValidator.Reason)

class HuntGenerationResult(
    val items: List<String>,
    /** Model suggestions that failed the safety validator (kept for tests and debugging only). */
    val rejected: List<RejectedItem>,
    val attempts: Int,
    /** How many of [items] came from the built-in safe list instead of the model. */
    val fallbackCount: Int,
)

/**
 * Asks the on-device model for a hunt list and guarantees what comes out is safe and complete:
 * every item passes [SafetyValidator]; unsafe or repeated items are rejected and replaced by asking
 * the model again (told what to avoid); invalid JSON gets one retry; and if the model still cannot
 * deliver, the built-in safe list fills the gap. It never fails for model reasons, only if the model
 * file is missing ([mx.dev1.naturequest.domain.inference.ModelNotAvailableException]).
 *
 * The engine is released when done: the next use is minutes away and the model is large.
 */
class GenerateHuntUseCase @Inject constructor(
    private val engine: InferenceEngine,
    private val prompts: PromptSource,
    private val fallback: FallbackItems,
    private val parser: ModelJsonParser,
    private val validator: SafetyValidator,
) {
    suspend operator fun invoke(
        settings: HuntSettings,
        locale: Locale = Locale.getDefault(),
        date: LocalDate = LocalDate.now(),
        onProgress: (HuntGenerationProgress) -> Unit = {},
    ): HuntGenerationResult {
        val target = settings.length.items
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<RejectedItem>()
        var attempts = 0
        var invalidResponses = 0
        try {
            onProgress(HuntGenerationProgress.LOADING_MODEL)
            engine.load()
            onProgress(HuntGenerationProgress.WRITING_LIST)
            while (accepted.size < target && attempts < MAX_ATTEMPTS && invalidResponses < MAX_INVALID_RESPONSES) {
                attempts++
                // Ask for a couple extra so one pass is usually enough even if some are rejected.
                val requested = target - accepted.size + EXTRA_ITEMS
                val prompt = prompts.render(
                    PROMPT_NAME,
                    mapOf(
                        "count" to requested.toString(),
                        "place" to settings.place.promptName,
                        "month" to date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        "age_range" to settings.ageRange.promptName,
                        "language" to locale.getDisplayLanguage(Locale.ENGLISH),
                        "avoid" to avoidList(accepted, rejected),
                    ),
                )
                val response = try {
                    engine.generate(
                        InferenceRequest(
                            prompt = prompt,
                            temperature = 0.8,
                            maxOutputTokens = maxOf(MIN_OUTPUT_TOKENS, requested * TOKENS_PER_ITEM),
                            // Without a fresh seed the engine repeats the same hunt for the same settings.
                            seed = Random.nextInt(),
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A native engine error is treated like invalid output: retry once, then fall back.
                    invalidResponses++
                    continue
                }
                val parsed = parser.parseHuntList(response.text).getOrNull()
                if (parsed == null) {
                    invalidResponses++
                    continue
                }
                for (candidate in parsed.items) {
                    if (accepted.size == target) break
                    when (val verdict = validator.validate(candidate, accepted)) {
                        is SafetyValidator.Verdict.Valid -> accepted += verdict.text
                        is SafetyValidator.Verdict.Rejected -> rejected += RejectedItem(candidate, verdict.reason)
                    }
                }
            }
        } finally {
            engine.release()
        }
        val fromFallback = fillFromFallback(accepted, target)
        return HuntGenerationResult(
            items = accepted.toList(),
            rejected = rejected,
            attempts = attempts,
            fallbackCount = fromFallback,
        )
    }

    /** Appends safe built-in items until the list has [target] items. Returns how many were added. */
    private fun fillFromFallback(accepted: MutableList<String>, target: Int): Int {
        var added = 0
        for (candidate in fallback.items()) {
            if (accepted.size == target) break
            val verdict = validator.validate(candidate, accepted)
            if (verdict is SafetyValidator.Verdict.Valid) {
                accepted += verdict.text
                added++
            }
        }
        return added
    }

    private fun avoidList(accepted: List<String>, rejected: List<RejectedItem>): String =
        (accepted + rejected.map { it.text }).takeLast(MAX_AVOID_ITEMS)
            .joinToString(", ") { "\"$it\"" }
            .ifEmpty { "nothing yet" }

    private companion object {
        const val PROMPT_NAME = "hunt_generation_v2"
        const val MAX_ATTEMPTS = 3
        const val MAX_INVALID_RESPONSES = 2 // one retry after invalid JSON, then fall back
        const val EXTRA_ITEMS = 2
        const val TOKENS_PER_ITEM = 20
        const val MIN_OUTPUT_TOKENS = 200
        const val MAX_AVOID_ITEMS = 20
    }
}
