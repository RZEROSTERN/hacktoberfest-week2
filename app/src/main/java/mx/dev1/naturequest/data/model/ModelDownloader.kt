package mx.dev1.naturequest.data.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import mx.dev1.naturequest.domain.model.DownloadFailure
import mx.dev1.naturequest.domain.model.DownloadFailureReason
import mx.dev1.naturequest.domain.model.DownloadProgress
import mx.dev1.naturequest.domain.model.ModelDownloading
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads the model into [directory]. It writes to `<name>.part`, continues from what is already
 * there with an HTTP `Range` request, retries when the connection drops, checks the SHA-256 of the
 * finished file and only then renames it to its final name, so a half-downloaded or corrupt file is
 * never mistaken for the model. Cancelling the collector stops it and keeps the `.part` file.
 */
class ModelDownloader(
    private val spec: ModelSpec,
    private val directory: File,
    private val source: DownloadSource,
    /** Free bytes available to the app in the given directory (on Android, including clearable cache). */
    private val freeSpaceBytes: (File) -> Long,
    private val retryDelaysMillis: List<Long> = listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L),
    private val spaceMarginBytes: Long = 200L * MEGABYTE,
) : ModelDownloading {

    override val totalBytes: Long get() = spec.sizeBytes

    private val target get() = File(directory, spec.fileName)
    private val part get() = File(directory, spec.fileName + PART_SUFFIX)

    override fun download(): Flow<DownloadProgress> = flow {
        directory.mkdirs()
        checkSpace()
        var retries = 0
        while (part.length() < spec.sizeBytes) {
            try {
                fetchRemaining { emit(it) }
            } catch (e: IOException) {
                if (retries >= retryDelaysMillis.size) throw DownloadFailure(DownloadFailureReason.NETWORK, e)
                delay(retryDelaysMillis[retries++])
            }
        }
        verifyChecksum { emit(it) }
        if (!part.renameTo(target)) throw DownloadFailure(DownloadFailureReason.SERVER, IOException("Could not move the file into place"))
        emit(DownloadProgress.Done)
    }.flowOn(Dispatchers.IO)

    private fun checkSpace() {
        val needed = spec.sizeBytes - part.length() + spaceMarginBytes
        if (freeSpaceBytes(directory) < needed) throw DownloadFailure(DownloadFailureReason.NOT_ENOUGH_SPACE)
    }

    /** Requests the missing bytes and appends them to the `.part` file, reporting progress as it goes. */
    private suspend fun fetchRemaining(report: suspend (DownloadProgress) -> Unit) {
        val have = part.length()
        source.open(spec.url, have).use { response ->
            val resuming = when (response.code) {
                206 -> {
                    if (response.startByte != have) throw IOException("Server resumed at ${response.startByte}, wanted $have")
                    true
                }
                200 -> false // the server ignored the Range header: start again from zero
                else -> throw DownloadFailure(DownloadFailureReason.SERVER, IOException("HTTP ${response.code}"))
            }
            if (response.totalBytes > 0 && response.totalBytes != spec.sizeBytes) {
                throw DownloadFailure(
                    DownloadFailureReason.SERVER,
                    IOException("Server says ${response.totalBytes} bytes, expected ${spec.sizeBytes}"),
                )
            }
            var written = if (resuming) have else 0L
            var sinceReport = 0L
            val buffer = ByteArray(BUFFER_BYTES)
            FileOutputStream(part, resuming).use { out ->
                report(DownloadProgress.Downloading(written, spec.sizeBytes))
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = response.body.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    written += read
                    sinceReport += read
                    if (sinceReport >= REPORT_EVERY_BYTES) {
                        sinceReport = 0
                        report(DownloadProgress.Downloading(written, spec.sizeBytes))
                    }
                }
                // Progress is throttled, so report where the transfer really ended.
                report(DownloadProgress.Downloading(written, spec.sizeBytes))
            }
            if (part.length() < spec.sizeBytes) throw IOException("Connection ended after ${part.length()} of ${spec.sizeBytes} bytes")
        }
    }

    /** Checks size and SHA-256. A bad file is deleted so the next try starts clean. */
    private suspend fun verifyChecksum(report: suspend (DownloadProgress) -> Unit) {
        if (part.length() != spec.sizeBytes) {
            part.delete()
            throw DownloadFailure(DownloadFailureReason.CORRUPT, IOException("Wrong size: ${part.length()}"))
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var checked = 0L
        var sinceReport = 0L
        report(DownloadProgress.Verifying(0, spec.sizeBytes))
        part.inputStream().use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                checked += read
                sinceReport += read
                if (sinceReport >= VERIFY_REPORT_EVERY_BYTES) {
                    sinceReport = 0
                    report(DownloadProgress.Verifying(checked, spec.sizeBytes))
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(spec.sha256, ignoreCase = true)) {
            part.delete()
            throw DownloadFailure(DownloadFailureReason.CORRUPT, IOException("Checksum mismatch: $actual"))
        }
    }

    companion object {
        const val PART_SUFFIX = ".part"
        private const val MEGABYTE = 1024L * 1024L
        private const val BUFFER_BYTES = 64 * 1024
        private const val REPORT_EVERY_BYTES = 512L * 1024L
        private const val VERIFY_REPORT_EVERY_BYTES = 64L * MEGABYTE
    }
}
