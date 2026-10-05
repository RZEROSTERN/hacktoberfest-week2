package mx.dev1.naturequest.debug

import kotlinx.serialization.Serializable
import mx.dev1.naturequest.data.inference.InferenceBackend
import mx.dev1.naturequest.domain.inference.InferenceStats

data class SpikeConfig(
    val backend: InferenceBackend = InferenceBackend.CPU,
    /** Null means the image encoder uses the same backend as the text model. */
    val visionBackend: InferenceBackend? = null,
    val maxImageSidePx: Int = 640,
    val visualTokenBudget: Int = 140,
    val maxNumTokens: Int = 2048,
    /** How many of the built-in hunt generation cases to run (max 4). */
    val generationCases: Int = 4,
    /** Also verify every sample against a different label; the model should say "no match". */
    val includeNegatives: Boolean = true,
    /** Release the engine and load it again to measure the cached (second) load. */
    val measureSecondLoad: Boolean = true,
)

@Serializable
data class MemoryPoint(val label: String, val vmHwmMb: Long, val vmRssMb: Long, val pssMb: Long)

@Serializable
data class GenerationRun(
    val place: String,
    val count: Int,
    val ageRange: String,
    val language: String,
    val parsedOk: Boolean,
    val itemCount: Int,
    val countMatches: Boolean,
    /** Items containing risky words (rough keyword check, only to inform the real validator). */
    val flaggedItems: List<String>,
    val rawText: String,
    val stats: InferenceStats? = null,
    val error: String? = null,
)

@Serializable
data class VerificationRun(
    val sample: String,
    val item: String,
    val expectedMatch: Boolean,
    val parsedOk: Boolean,
    val match: Boolean? = null,
    val correct: Boolean,
    val message: String? = null,
    val hint: String? = null,
    val rawText: String,
    val prepMs: Long,
    val stats: InferenceStats? = null,
    val error: String? = null,
)

@Serializable
data class SpikeSummary(
    val generationMsAvg: Long?,
    val generationMsMax: Long?,
    val generationParsedOk: String,
    val verificationMsAvg: Long?,
    val verificationMsMax: Long?,
    val positivesCorrect: String,
    val negativesCorrect: String,
    val verificationParsedOk: String,
)

@Serializable
data class SpikeReport(
    val startedAt: String,
    val device: String,
    val modelFile: String,
    val modelBytes: Long,
    val backend: String,
    val visionBackend: String,
    val maxImageSidePx: Int,
    val visualTokenBudget: Int,
    val maxNumTokens: Int,
    val firstLoadMs: Long? = null,
    val secondLoadMs: Long? = null,
    val loadError: String? = null,
    val memory: List<MemoryPoint> = emptyList(),
    val generation: List<GenerationRun> = emptyList(),
    val verification: List<VerificationRun> = emptyList(),
    /** Latency-only runs on a drawn image, used when /samples is empty. Never scored for accuracy. */
    val synthetic: List<VerificationRun> = emptyList(),
    val summary: SpikeSummary? = null,
)
