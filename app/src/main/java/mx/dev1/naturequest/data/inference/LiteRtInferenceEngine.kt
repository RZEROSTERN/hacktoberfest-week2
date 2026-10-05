package mx.dev1.naturequest.data.inference

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mx.dev1.naturequest.data.model.ModelStore
import mx.dev1.naturequest.domain.inference.InferenceEngine
import mx.dev1.naturequest.domain.inference.InferenceRequest
import mx.dev1.naturequest.domain.inference.InferenceResult
import mx.dev1.naturequest.domain.inference.InferenceStats
import kotlin.coroutines.cancellation.CancellationException

/**
 * [InferenceEngine] backed by LiteRT-LM running Gemma 4 on the phone. This is the only class that
 * touches LiteRT-LM types. Requests are serialized: the engine runs one conversation at a time.
 */
@OptIn(ExperimentalApi::class)
class LiteRtInferenceEngine(
    private val context: Context,
    private val modelStore: ModelStore,
    private val options: EngineOptions = EngineOptions(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : InferenceEngine {

    private val mutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    override val isLoaded: Boolean get() = engine != null

    override suspend fun load() {
        withContext(dispatcher) {
            mutex.withLock {
                if (engine != null) return@withLock
                check(modelStore.isAvailable()) { "Model file not found: ${modelStore.modelFile()}" }
                // Engine-reported timings (time to first token, tokens per second).
                ExperimentalFlags.enableBenchmark = true
                ExperimentalFlags.visualTokenBudget = options.visualTokenBudget
                val created = Engine(
                    EngineConfig(
                        modelPath = modelStore.modelFile().absolutePath,
                        backend = options.backend.toLiteRt(),
                        visionBackend = (options.visionBackend ?: options.backend).toLiteRt(),
                        maxNumTokens = options.maxNumTokens,
                        maxNumImages = 1,
                        // Writable dir; LiteRT-LM uses it to speed up the second load.
                        cacheDir = context.cacheDir.path,
                    ),
                )
                created.initialize()
                engine = created
            }
        }
    }

    override suspend fun generate(request: InferenceRequest): InferenceResult = withContext(dispatcher) {
        mutex.withLock {
            val loaded = checkNotNull(engine) { "Model is not loaded" }
            val startedAt = SystemClock.elapsedRealtime()
            val config = ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = request.temperature),
                maxOutputToken = request.maxOutputTokens,
                thinkingConfig = ThinkingConfig(enableThinking = false),
            )
            loaded.createConversation(config).use { conversation ->
                val contents = if (request.imageJpeg != null) {
                    // The image goes first, then the question (order used by the official docs).
                    Contents.of(Content.ImageBytes(request.imageJpeg), Content.Text(request.prompt))
                } else {
                    Contents.of(request.prompt)
                }
                val text = StringBuilder()
                try {
                    conversation.sendMessageAsync(contents).collect { text.append(it.toString()) }
                } catch (e: CancellationException) {
                    // Cancelling the Flow does not stop native inference; it must be stopped explicitly.
                    runCatching { conversation.cancelProcess() }
                    throw e
                }
                val totalMs = SystemClock.elapsedRealtime() - startedAt
                val info = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
                InferenceResult(
                    text = text.toString().trim(),
                    stats = InferenceStats(
                        totalMs = totalMs,
                        timeToFirstTokenMs = info?.let { (it.timeToFirstTokenInSecond * 1000).toLong() },
                        prefillTokens = info?.lastPrefillTokenCount,
                        decodeTokens = info?.lastDecodeTokenCount,
                        prefillTokensPerSecond = info?.lastPrefillTokensPerSecond,
                        decodeTokensPerSecond = info?.lastDecodeTokensPerSecond,
                    ),
                )
            }
        }
    }

    override suspend fun release() {
        // Not cancellable: a half-closed engine would leak gigabytes of native memory.
        withContext(NonCancellable + dispatcher) {
            mutex.withLock {
                engine?.close()
                engine = null
            }
        }
    }

    private fun InferenceBackend.toLiteRt(): Backend = when (this) {
        InferenceBackend.CPU -> Backend.CPU()
        InferenceBackend.GPU -> Backend.GPU()
    }
}
