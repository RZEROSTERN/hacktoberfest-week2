package mx.dev1.naturequest.data.model

import java.io.Closeable
import java.io.InputStream

/**
 * One HTTP answer. [startByte] is the offset of the first byte of [body] in the whole file (0 for a
 * full answer, the requested offset for a 206), and [totalBytes] the size of the whole file (-1 if the
 * server did not say).
 */
class DownloadResponse(
    val code: Int,
    val startByte: Long,
    val totalBytes: Long,
    val body: InputStream,
    private val onClose: () -> Unit = {},
) : Closeable {
    override fun close() {
        runCatching { body.close() }
        onClose()
    }
}

/** Opens a download, asking the server to start at [startByte] when it is above zero. */
fun interface DownloadSource {
    fun open(url: String, startByte: Long): DownloadResponse
}
