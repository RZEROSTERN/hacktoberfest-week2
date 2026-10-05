package mx.dev1.naturequest.ui.setup

import androidx.annotation.StringRes
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.PlaceType

@StringRes
fun PlaceType.labelRes(): Int = when (this) {
    PlaceType.PARK -> R.string.place_park
    PlaceType.FOREST -> R.string.place_forest
    PlaceType.GARDEN -> R.string.place_garden
    PlaceType.URBAN_WALK -> R.string.place_urban_walk
}

@StringRes
fun HuntLength.labelRes(): Int = when (this) {
    HuntLength.SHORT -> R.string.length_short
    HuntLength.MEDIUM -> R.string.length_medium
    HuntLength.LONG -> R.string.length_long
}

@StringRes
fun AgeRange.labelRes(): Int = when (this) {
    AgeRange.AGES_4_TO_6 -> R.string.age_4_to_6
    AgeRange.AGES_7_TO_9 -> R.string.age_7_to_9
    AgeRange.AGES_10_PLUS -> R.string.age_10_plus
}
