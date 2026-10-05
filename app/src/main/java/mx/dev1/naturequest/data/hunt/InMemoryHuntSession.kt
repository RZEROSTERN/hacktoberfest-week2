package mx.dev1.naturequest.data.hunt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mx.dev1.naturequest.domain.hunt.HuntItem
import mx.dev1.naturequest.domain.hunt.HuntSession
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.HuntState
import mx.dev1.naturequest.domain.hunt.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryHuntSession @Inject constructor(
    private val time: TimeSource,
) : HuntSession {
    private val _state = MutableStateFlow<HuntState?>(null)
    override val state: StateFlow<HuntState?> = _state.asStateFlow()

    override fun start(settings: HuntSettings, items: List<String>) {
        _state.value = HuntState(
            settings = settings,
            items = items.mapIndexed { index, text -> HuntItem(id = index, text = text) },
            startedAtMillis = time.nowMillis(),
        )
    }

    override fun markFound(itemId: Int, photoPath: String?) {
        _state.update { hunt ->
            hunt?.copy(
                items = hunt.items.map { if (it.id == itemId) it.copy(found = true, photoPath = photoPath) else it },
            )
        }
    }

    override fun finish() {
        _state.update { hunt -> hunt?.takeIf { !it.finished }?.copy(endedAtMillis = time.nowMillis()) ?: hunt }
    }

    override fun clear() {
        _state.value = null
    }
}
