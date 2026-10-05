package mx.dev1.naturequest.domain.hunt

import kotlinx.coroutines.flow.StateFlow

data class HuntItem(
    val id: Int,
    val text: String,
    val found: Boolean = false,
    /** App-private cached photo of this find, kept only until the hunt ends. */
    val photoPath: String? = null,
)

data class HuntState(
    val settings: HuntSettings,
    val items: List<HuntItem>,
    val startedAtMillis: Long,
    /** Set when the hunt is finished; the hunt then stays available for the summary. */
    val endedAtMillis: Long? = null,
) {
    val finished: Boolean get() = endedAtMillis != null

    val foundCount: Int get() = items.count { it.found }

    val allFound: Boolean get() = items.isNotEmpty() && items.all { it.found }

    /** Whole minutes between the start and the end of the hunt (0 while it is still running). */
    val minutesOutside: Int
        get() = (((endedAtMillis ?: startedAtMillis) - startedAtMillis).coerceAtLeast(0) / MILLIS_PER_MINUTE).toInt()

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

/** The hunt in progress. Lives in memory only: a hunt is a short outing, not saved data. */
interface HuntSession {
    val state: StateFlow<HuntState?>

    /** Begins a hunt and starts its clock. */
    fun start(settings: HuntSettings, items: List<String>)

    fun markFound(itemId: Int, photoPath: String?)

    /** Stops the clock. The hunt stays available so the summary can show it. */
    fun finish()

    /** Forgets the hunt completely. */
    fun clear()
}

/** The current time, so the hunt clock can be tested. */
fun interface TimeSource {
    fun nowMillis(): Long
}
