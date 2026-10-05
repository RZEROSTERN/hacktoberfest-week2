package mx.dev1.naturequest.domain.model

import kotlinx.coroutines.flow.Flow

/** Progress of the one-time model download. [total] is the final size of the file. */
sealed interface DownloadProgress {
    data class Downloading(val downloaded: Long, val total: Long) : DownloadProgress

    /** The whole file is on the phone and its checksum is being verified. */
    data class Verifying(val checked: Long, val total: Long) : DownloadProgress

    data object Done : DownloadProgress
}

enum class DownloadFailureReason {
    NOT_ENOUGH_SPACE,

    /** The connection kept failing. What was downloaded is kept, so it can continue later. */
    NETWORK,

    /** The server answered with something unexpected (not found, wrong size...). */
    SERVER,

    /** The finished file did not match its checksum and was deleted. */
    CORRUPT,
}

class DownloadFailure(val reason: DownloadFailureReason, cause: Throwable? = null) :
    Exception("Model download failed: $reason", cause)

/** Whether the on-device model is ready to use, and how much of it is already downloaded. */
interface ModelAvailability {
    fun isAvailable(): Boolean

    /** Bytes of an interrupted download that can be continued (0 if none). */
    fun partialBytes(): Long
}

/** Downloads the model. Collecting starts or continues the download; cancelling the collector stops it. */
interface ModelDownloading {
    /** Size of the finished file, for showing "2.6 GB" before it starts. */
    val totalBytes: Long

    /** Emits progress and ends with [DownloadProgress.Done], or throws [DownloadFailure]. */
    fun download(): Flow<DownloadProgress>
}

/** What kind of connection the phone is on, so a 2.6 GB download can warn about mobile data. */
interface NetworkInfo {
    fun isMetered(): Boolean
}
