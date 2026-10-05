package mx.dev1.naturequest.domain.inference

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Parses the model's JSON output. Models sometimes wrap JSON in prose or code fences, so the first
 * balanced JSON object in the text is extracted before decoding. Anything that does not decode
 * into the expected shape is a failure; callers retry once and then use a safe fallback.
 */
class ModelJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parseHuntList(raw: String): Result<HuntListResponse> =
        parse(raw, HuntListResponse.serializer()).mapCatching {
            require(it.items.isNotEmpty() && it.items.none { item -> item.isBlank() }) {
                "Hunt list is empty or has blank items"
            }
            it
        }

    fun parseVerification(raw: String): Result<VerificationResponse> =
        parse(raw, VerificationResponse.serializer()).mapCatching {
            require(it.message.isNotBlank()) { "Verification message is blank" }
            it
        }

    private fun <T> parse(raw: String, serializer: KSerializer<T>): Result<T> = runCatching {
        val jsonObject = extractJsonObject(raw) ?: error("No JSON object found in model output")
        json.decodeFromString(serializer, jsonObject)
    }
}

/** Returns the first balanced `{...}` in [raw], ignoring braces inside string literals. */
internal fun extractJsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until raw.length) {
        val c = raw[i]
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '{' -> depth++
            c == '}' -> {
                depth--
                if (depth == 0) return raw.substring(start, i + 1)
            }
        }
    }
    return null
}
