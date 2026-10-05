package mx.dev1.naturequest.domain.verification

enum class VerificationOutcome {
    /** The model says the photo shows the item. */
    MATCH,

    /** The model says it shows something else. */
    NO_MATCH,

    /** The model gave no usable answer; the player is asked to try another photo. */
    NOT_SURE,
}

/** [message] and [hint] are safe to show and read aloud to children. */
class PhotoVerification(
    val outcome: VerificationOutcome,
    val message: String,
    val hint: String?,
)

/** Turns a raw camera shot into the small JPEG sent to the model. Everything stays in memory. */
interface PhotoPreprocessor {
    suspend fun prepare(raw: ByteArray, rotationDegrees: Int): ByteArray
}

/** Keeps found photos in app-private cache until the hunt ends. Nothing is ever uploaded. */
interface PhotoStore {
    /** Returns the saved file's path, or null if it could not be written. */
    suspend fun save(itemId: Int, jpeg: ByteArray): String?

    /** Deletes every cached photo. */
    fun clear()
}

/** Safe texts in the device language for when the model's own words cannot be used. */
interface VerificationFallbacks {
    fun notSure(): String

    fun genericMatch(): String

    fun genericMiss(): String
}
