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
)

/** The hunt in progress. Lives in memory only: a hunt is a short outing, not saved data. */
interface HuntSession {
    val state: StateFlow<HuntState?>

    fun start(settings: HuntSettings, items: List<String>)

    fun markFound(itemId: Int, photoPath: String?)

    fun end()
}
