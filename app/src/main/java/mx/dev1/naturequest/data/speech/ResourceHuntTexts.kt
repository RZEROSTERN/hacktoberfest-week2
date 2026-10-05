package mx.dev1.naturequest.data.speech

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.hunt.Medal
import mx.dev1.naturequest.domain.speech.HuntTexts
import javax.inject.Inject

/** What the hunt shows and says, from string resources so it follows the device language. */
class ResourceHuntTexts @Inject constructor(
    @ApplicationContext private val context: Context,
) : HuntTexts {
    override fun huntReadySpeech(items: List<String>): String =
        context.getString(R.string.speech_hunt_ready, items.joinToString(separator = ". ", postfix = "."))

    override fun summarySpeech(found: Int, total: Int, minutesOutside: Int, medal: Medal): String =
        context.getString(R.string.speech_summary, found, total, timeOutside(minutesOutside), medalName(medal))

    override fun timeOutside(minutesOutside: Int): String = when {
        minutesOutside < 1 -> context.getString(R.string.summary_time_under_a_minute)
        minutesOutside < MINUTES_PER_HOUR ->
            context.resources.getQuantityString(R.plurals.summary_time_minutes, minutesOutside, minutesOutside)
        else -> context.getString(
            R.string.summary_time_hours,
            minutesOutside / MINUTES_PER_HOUR,
            minutesOutside % MINUTES_PER_HOUR,
        )
    }

    override fun foundOf(found: Int, total: Int): String = context.getString(R.string.summary_found, found, total)

    override fun medalName(medal: Medal): String = context.getString(
        when (medal) {
            Medal.GOLD -> R.string.medal_gold
            Medal.SILVER -> R.string.medal_silver
            Medal.BRONZE -> R.string.medal_bronze
        },
    )

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
