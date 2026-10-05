package mx.dev1.naturequest.ui.hunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.dev1.naturequest.domain.hunt.GenerateHuntUseCase
import mx.dev1.naturequest.domain.hunt.HuntGenerationProgress
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.inference.ModelNotAvailableException
import kotlin.coroutines.cancellation.CancellationException

sealed interface HuntUiState {
    data class Generating(val progress: HuntGenerationProgress) : HuntUiState
    data class Ready(val items: List<String>) : HuntUiState
    data class Failed(val reason: FailureReason) : HuntUiState
}

enum class FailureReason { MODEL_MISSING, GENERIC }

@HiltViewModel(assistedFactory = HuntViewModel.Factory::class)
class HuntViewModel @AssistedInject constructor(
    @Assisted private val settings: HuntSettings,
    private val generateHunt: GenerateHuntUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(settings: HuntSettings): HuntViewModel
    }

    private val _state = MutableStateFlow<HuntUiState>(HuntUiState.Generating(HuntGenerationProgress.LOADING_MODEL))
    val state: StateFlow<HuntUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        generate()
    }

    /** Starts (or restarts) generating the hunt list. Safe to call again after a failure. */
    fun generate() {
        job?.cancel()
        _state.value = HuntUiState.Generating(HuntGenerationProgress.LOADING_MODEL)
        job = viewModelScope.launch {
            _state.value = try {
                val result = generateHunt(settings, onProgress = { _state.value = HuntUiState.Generating(it) })
                HuntUiState.Ready(result.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelNotAvailableException) {
                HuntUiState.Failed(FailureReason.MODEL_MISSING)
            } catch (e: Exception) {
                HuntUiState.Failed(FailureReason.GENERIC)
            }
        }
    }

    /** Stops generation; the use case frees the model on its way out. */
    fun cancel() {
        job?.cancel()
    }
}
