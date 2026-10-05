package mx.dev1.naturequest.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mx.dev1.naturequest.domain.hunt.GallerySaver
import mx.dev1.naturequest.domain.hunt.Medal
import mx.dev1.naturequest.domain.hunt.TimeSource
import mx.dev1.naturequest.domain.inference.InferenceEngine
import mx.dev1.naturequest.domain.inference.InferenceRequest
import mx.dev1.naturequest.domain.inference.InferenceResult
import mx.dev1.naturequest.domain.inference.InferenceStats
import mx.dev1.naturequest.domain.prompt.PromptSource
import mx.dev1.naturequest.domain.speech.HuntTexts
import mx.dev1.naturequest.domain.speech.Speaker
import mx.dev1.naturequest.domain.verification.PhotoPreprocessor
import mx.dev1.naturequest.domain.verification.PhotoStore
import mx.dev1.naturequest.domain.verification.VerificationFallbacks

/**
 * Scripted stand-in for the on-device model. [responder] receives the request and the 1-based call
 * number and returns the raw text the model would have produced (it may also throw or suspend).
 */
class FakeInferenceEngine(
    private val responder: suspend (request: InferenceRequest, call: Int) -> String,
) : InferenceEngine {
    var loadCalls = 0
        private set
    var releaseCalls = 0
        private set
    val requests = mutableListOf<InferenceRequest>()
    var loadError: Exception? = null
    override var isLoaded = false
        private set

    override suspend fun load() {
        loadCalls++
        loadError?.let { throw it }
        isLoaded = true
    }

    override suspend fun generate(request: InferenceRequest): InferenceResult {
        requests += request
        return InferenceResult(responder(request, requests.size), InferenceStats(totalMs = 1))
    }

    override suspend fun release() {
        releaseCalls++
        isLoaded = false
    }

    companion object {
        /** Answers with the given texts in order, repeating the last one when they run out. */
        fun scripted(vararg responses: String) =
            FakeInferenceEngine { _, call -> responses[minOf(call, responses.size) - 1] }
    }
}

/** Records the values each prompt was rendered with. */
class FakePromptSource : PromptSource {
    val rendered = mutableListOf<Pair<String, Map<String, String>>>()

    override fun render(name: String, values: Map<String, String>): String {
        rendered += name to values
        return "$name: " + values.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}

/** Builds the JSON the model is asked to return. */
fun huntJson(vararg items: String): String =
    """{"items": [${items.joinToString(", ") { "\"$it\"" }}]}"""

/** Builds the JSON the model is asked to return for a photo check. */
fun verificationJson(match: Boolean, message: String, hint: String = ""): String =
    """{"match": $match, "message": "$message", "hint": "$hint"}"""

class FakePhotoPreprocessor : PhotoPreprocessor {
    val calls = mutableListOf<Pair<ByteArray, Int>>()

    /** The "small JPEG" the rest of the app receives. */
    val prepared = byteArrayOf(1, 2, 3)

    override suspend fun prepare(raw: ByteArray, rotationDegrees: Int): ByteArray {
        calls += raw to rotationDegrees
        return prepared
    }
}

class FakePhotoStore : PhotoStore {
    val saved = mutableMapOf<Int, ByteArray>()
    var clearCalls = 0
        private set

    override suspend fun save(itemId: Int, jpeg: ByteArray): String {
        saved[itemId] = jpeg
        return "/cache/item-$itemId.jpg"
    }

    override fun clear() {
        clearCalls++
        saved.clear()
    }
}

class FakeVerificationFallbacks : VerificationFallbacks {
    override fun notSure() = "NOT_SURE"

    override fun genericMatch() = "GENERIC_MATCH"

    override fun genericMiss() = "GENERIC_MISS"
}

/** Records what would have been said aloud. */
class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    var stopCalls = 0
        private set
    var releaseCalls = 0
        private set
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking

    override fun speak(text: String) {
        spoken += text
        _speaking.value = true
    }

    override fun stop() {
        stopCalls++
        _speaking.value = false
    }

    override fun release() {
        releaseCalls++
        _speaking.value = false
    }
}

class FakeHuntTexts : HuntTexts {
    override fun huntReadySpeech(items: List<String>) = "READY: " + items.joinToString("|")

    override fun summarySpeech(found: Int, total: Int, minutesOutside: Int, medal: Medal) =
        "SUMMARY $found/$total ${minutesOutside}min $medal"

    override fun timeOutside(minutesOutside: Int) = "${minutesOutside} min outside"

    override fun foundOf(found: Int, total: Int) = "found $found of $total"

    override fun medalName(medal: Medal) = "medal ${medal.name.lowercase()}"
}

class FakeGallerySaver(private val result: (List<String>) -> Int = { it.size }) : GallerySaver {
    val requests = mutableListOf<List<String>>()

    override suspend fun save(photoPaths: List<String>): Int {
        requests += photoPaths
        return result(photoPaths)
    }
}

/** A clock the test moves by hand. */
class FakeTimeSource(var now: Long = 1_000_000L) : TimeSource {
    override fun nowMillis() = now
}
