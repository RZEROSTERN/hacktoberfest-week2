package mx.dev1.naturequest.testing

import mx.dev1.naturequest.domain.inference.InferenceEngine
import mx.dev1.naturequest.domain.inference.InferenceRequest
import mx.dev1.naturequest.domain.inference.InferenceResult
import mx.dev1.naturequest.domain.inference.InferenceStats
import mx.dev1.naturequest.domain.prompt.PromptSource

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
