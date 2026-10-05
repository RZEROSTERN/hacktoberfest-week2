package mx.dev1.naturequest.data.model

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * [DownloadSource] on plain `HttpURLConnection` (no extra dependency). Hugging Face answers with a
 * redirect to a CDN, so redirects are followed by hand and the `Range` header is sent on every hop.
 * Only https is accepted, unless [allowInsecure] is set (for tests against a local server).
 */
class HttpDownloadSource(
    private val allowInsecure: Boolean = false,
    private val connectTimeoutMillis: Int = 20_000,
    private val readTimeoutMillis: Int = 30_000,
) : DownloadSource {

    override fun open(url: String, startByte: Long): DownloadResponse {
        var current = URL(url)
        repeat(MAX_REDIRECTS + 1) {
            requireAllowed(current)
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                setRequestProperty("User-Agent", USER_AGENT)
                if (startByte > 0) setRequestProperty("Range", "bytes=$startByte-")
            }
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirect without a Location")
                current = URL(current, location)
                return@repeat
            }
            val body = if (code in 200..299) connection.inputStream else connection.errorStream ?: emptyStream()
            val (start, total) = parseRange(connection, code, startByte)
            return DownloadResponse(code, start, total, body) { connection.disconnect() }
        }
        throw IOException("Too many redirects")
    }

    private fun requireAllowed(url: URL) {
        if (url.protocol != "https" && !allowInsecure) throw IOException("Refusing a non-https download: ${url.protocol}")
    }

    /** Reads where the body starts and the size of the whole file from the headers. */
    private fun parseRange(connection: HttpURLConnection, code: Int, requested: Long): Pair<Long, Long> {
        val contentRange = connection.getHeaderField("Content-Range")
        if (code == 206 && contentRange != null) {
            // "bytes 1000-2999/3000"
            val match = CONTENT_RANGE.matchEntire(contentRange.trim())
            if (match != null) {
                val (from, _, total) = match.destructured
                return from.toLong() to (total.toLongOrNull() ?: -1L)
            }
        }
        val length = connection.getHeaderField("Content-Length")?.toLongOrNull()
        return when (code) {
            206 -> requested to (length?.plus(requested) ?: -1L)
            else -> 0L to (length ?: -1L)
        }
    }

    private fun emptyStream() = java.io.ByteArrayInputStream(ByteArray(0))

    private companion object {
        const val MAX_REDIRECTS = 6
        const val USER_AGENT = "NatureQuest/1.0 (Android)"
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
    }
}
