package mx.dev1.naturequest.domain.hunt

/** Copies photos into the phone's gallery. Only used when the player taps "Save to gallery". */
interface GallerySaver {
    /** Returns how many of [photoPaths] were saved. */
    suspend fun save(photoPaths: List<String>): Int
}
