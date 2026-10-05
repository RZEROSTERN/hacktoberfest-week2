package mx.dev1.naturequest.ui.hunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.dev1.naturequest.domain.hunt.GenerateHuntUseCase
import mx.dev1.naturequest.domain.hunt.HuntGenerationProgress
import mx.dev1.naturequest.domain.hunt.HuntItem
import mx.dev1.naturequest.domain.hunt.HuntSession
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.inference.ModelNotAvailableException
import mx.dev1.naturequest.domain.speech.HuntTexts
import mx.dev1.naturequest.domain.speech.Speaker
import mx.dev1.naturequest.domain.verification.PhotoStore
import kotlin.coroutines.cancellation.CancellationException

sealed interface HuntUiState {
    data class Generating(val progress: HuntGenerationProgress) : HuntUiState
    data class Ready(val items: List<HuntItem>) : HuntUiState
    data class Failed(val reason: FailureReason) : HuntUiState
}

enum class FailureReason { MODEL_MISSING, GENERIC }

@HiltViewModel(assistedFactory = HuntViewModel.Factory::class)
class HuntViewModel @AssistedInject constructor(
    @Assisted private val settings: HuntSettings,
    private val generateHunt: GenerateHuntUseCase,
    private val session: HuntSession,
    private val photoStore: PhotoStore,
    private val speaker: Speaker,
    private val texts: HuntTexts,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(settings: HuntSettings): HuntViewModel
    }

    private sealed interface Generation {
        data class Running(val progress: HuntGenerationProgress) : Generation
        data object Done : Generation
        data class Failed(val reason: FailureReason) : Generation
    }

    private val generation = MutableStateFlow<Generation>(Generation.Running(HuntGenerationProgress.LOADING_MODEL))

    /** Generation progress, then the live checklist: found items update as photos are verified. */
    val state: StateFlow<HuntUiState> = combine(generation, session.state) { generation, hunt ->
        when (generation) {
            is Generation.Running -> HuntUiState.Generating(generation.progress)
            is Generation.Failed -> HuntUiState.Failed(generation.reason)
            Generation.Done -> hunt?.let { HuntUiState.Ready(it.items) }
                ?: HuntUiState.Generating(HuntGenerationProgress.WRITING_LIST)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HuntUiState.Generating(HuntGenerationProgress.LOADING_MODEL))

    /** True while the list is being read aloud (the button then offers to stop). */
    val speaking: StateFlow<Boolean> = speaker.speaking

    private var job: Job? = null

    init {
        generate()
    }

    /** Starts (or restarts) generating the hunt list. Safe to call again after a failure. */
    fun generate() {
        job?.cancel()
        generation.value = Generation.Running(HuntGenerationProgress.LOADING_MODEL)
        job = viewModelScope.launch {
            generation.value = try {
                val result = generateHunt(settings, onProgress = { generation.value = Generation.Running(it) })
                session.start(settings, result.items)
                speaker.speak(texts.huntReadySpeech(result.items))
                Generation.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelNotAvailableException) {
                Generation.Failed(FailureReason.MODEL_MISSING)
            } catch (e: Exception) {
                Generation.Failed(FailureReason.GENERIC)
            }
        }
    }

    /** Stops generation; the use case frees the model on its way out. */
    fun cancel() {
        job?.cancel()
    }

    /** Reads the whole list aloud again. */
    fun readAloud() {
        val items = session.state.value?.items?.map { it.text } ?: return
        speaker.speak(texts.huntReadySpeech(items))
    }

    fun stopReading() = speaker.stop()

    /** Ends the hunt for the summary: stops the clock and the voice; the hunt stays until the summary closes. */
    fun finish() {
        speaker.stop()
        session.finish()
    }

    override fun onCleared() {
        // A finished hunt now belongs to the summary screen, which cleans up when it closes (and
        // keeps speaking). Leaving an unfinished hunt forgets it and deletes the photos of the finds.
        if (session.state.value?.finished != true) {
            speaker.stop()
            speaker.release()
            session.clear()
            photoStore.clear()
        }
    }
}
