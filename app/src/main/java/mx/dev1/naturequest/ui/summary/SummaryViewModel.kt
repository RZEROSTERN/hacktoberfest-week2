package mx.dev1.naturequest.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.dev1.naturequest.domain.hunt.GallerySaver
import mx.dev1.naturequest.domain.hunt.HuntSession
import mx.dev1.naturequest.domain.hunt.HuntState
import mx.dev1.naturequest.domain.hunt.Medal
import mx.dev1.naturequest.domain.speech.HuntTexts
import mx.dev1.naturequest.domain.speech.Speaker
import mx.dev1.naturequest.domain.verification.PhotoStore
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class SummaryItem(val text: String, val found: Boolean, val photoPath: String?)

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Saved(val count: Int) : SaveState
    data object Failed : SaveState
}

sealed interface SummaryUiState {
    /** No hunt to show (for example the app was restarted). */
    data object Empty : SummaryUiState

    class Ready(
        val foundText: String,
        val timeText: String,
        val medal: Medal,
        val medalName: String,
        val items: List<SummaryItem>,
        val save: SaveState = SaveState.Idle,
    ) : SummaryUiState {
        val photoPaths: List<String> get() = items.mapNotNull { it.photoPath }
    }
}

/**
 * Shows how the hunt went and reads it aloud. Photos of the finds are kept only while this screen is
 * open: leaving it deletes the cache, unless the player chose to save them to the gallery.
 */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val session: HuntSession,
    private val photoStore: PhotoStore,
    private val speaker: Speaker,
    private val texts: HuntTexts,
    private val gallery: GallerySaver,
) : ViewModel() {

    private val _state = MutableStateFlow(session.state.value?.let(::buildReady) ?: SummaryUiState.Empty)
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    init {
        session.state.value?.let { hunt ->
            val medal = Medal.forResult(hunt.foundCount, hunt.items.size)
            speaker.speak(texts.summarySpeech(hunt.foundCount, hunt.items.size, hunt.minutesOutside, medal))
        }
    }

    private fun buildReady(hunt: HuntState): SummaryUiState.Ready {
        val medal = Medal.forResult(hunt.foundCount, hunt.items.size)
        return SummaryUiState.Ready(
            foundText = texts.foundOf(hunt.foundCount, hunt.items.size),
            timeText = texts.timeOutside(hunt.minutesOutside),
            medal = medal,
            medalName = texts.medalName(medal),
            items = hunt.items.map { SummaryItem(it.text, it.found, it.photoPath) },
        )
    }

    /** Copies the photos of the finds into the phone's gallery. Only runs when the player taps the button. */
    fun saveToGallery() {
        val ready = _state.value as? SummaryUiState.Ready ?: return
        val paths = ready.photoPaths
        if (paths.isEmpty() || ready.save is SaveState.Saving || ready.save is SaveState.Saved) return
        _state.update { (it as SummaryUiState.Ready).withSave(SaveState.Saving) }
        viewModelScope.launch {
            val saved = try {
                gallery.save(paths)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0
            }
            _state.update { (it as SummaryUiState.Ready).withSave(if (saved > 0) SaveState.Saved(saved) else SaveState.Failed) }
        }
    }

    fun stopSpeaking() = speaker.stop()

    override fun onCleared() {
        speaker.stop()
        speaker.release()
        session.clear()
        photoStore.clear()
    }

    private fun SummaryUiState.Ready.withSave(save: SaveState) =
        SummaryUiState.Ready(foundText, timeText, medal, medalName, items, save)
}
