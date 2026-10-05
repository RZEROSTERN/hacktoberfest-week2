package mx.dev1.naturequest.domain.inference

import kotlinx.serialization.Serializable

/**
 * On-device model access. ViewModels and use cases depend on this interface only, never on
 * LiteRT-LM, so tests can swap in a fake.
 *
 * All calls run off the main thread and are safe to cancel: cancelling the calling coroutine
 * stops generation.
 */
interface InferenceEngine {
    val isLoaded: Boolean

    /** Loads the model into memory. Idempotent. Slow (seconds): show progress. */
    suspend fun load()

    /** Runs one stateless request. Requires [load] first. */
    suspend fun generate(request: InferenceRequest): InferenceResult

    /** Frees the model's memory. Call when the engine is not needed (it holds gigabytes). */
    suspend fun release()
}

class InferenceRequest(
    val prompt: String,
    /** Optional photo as JPEG bytes. Kept in memory only, never written to disk or uploaded. */
    val imageJpeg: ByteArray? = null,
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 512,
)

class InferenceResult(
    val text: String,
    val stats: InferenceStats,
)

/** Timings for one request. Engine-reported values are null when unavailable. */
@Serializable
data class InferenceStats(
    val totalMs: Long,
    val timeToFirstTokenMs: Long? = null,
    val prefillTokens: Int? = null,
    val decodeTokens: Int? = null,
    val prefillTokensPerSecond: Double? = null,
    val decodeTokensPerSecond: Double? = null,
)
