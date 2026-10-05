package mx.dev1.naturequest.domain.hunt

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class PlaceType(val promptName: String) {
    PARK("park"),
    FOREST("forest"),
    GARDEN("garden"),
    URBAN_WALK("urban walk"),
}

@Keep
@Serializable
enum class HuntLength(val items: Int) {
    SHORT(5),
    MEDIUM(8),
    LONG(12),
}

/** Age range of the youngest player; it sets how simple the items are. */
@Keep
@Serializable
enum class AgeRange(val promptName: String) {
    AGES_4_TO_6("4-6"),
    AGES_7_TO_9("7-9"),
    AGES_10_PLUS("10 and older"),
}

data class HuntSettings(
    val place: PlaceType,
    val length: HuntLength,
    val ageRange: AgeRange,
)
