package mx.dev1.naturequest.ui.verify

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
import mx.dev1.naturequest.domain.hunt.HuntSession
import mx.dev1.naturequest.domain.inference.ModelNotAvailableException
import mx.dev1.naturequest.domain.speech.Speaker
import mx.dev1.naturequest.domain.verification.PhotoPreprocessor
import mx.dev1.naturequest.domain.verification.PhotoStore
import mx.dev1.naturequest.domain.verification.PhotoVerification
import mx.dev1.naturequest.domain.verification.VerificationOutcome
import mx.dev1.naturequest.domain.verification.VerifyPhotoUseCase
import mx.dev1.naturequest.ui.hunt.FailureReason
import kotlin.coroutines.cancellation.CancellationException

sealed interface VerifyUiState {
    /** Camera is open, waiting for the photo. */
    data object Capturing : VerifyUiState

    /** [photo] is the small JPEG being checked (null for the instant while it is being prepared). */
    class Checking(val photo: ByteArray?) : VerifyUiState

    class Result(val photo: ByteArray, val verification: PhotoVerification) : VerifyUiState

    data class Failed(val reason: FailureReason) : VerifyUiState
}

@HiltViewModel(assistedFactory = VerifyViewModel.Factory::class)
class VerifyViewModel @AssistedInject constructor(
    @Assisted private val itemId: Int,
    private val session: HuntSession,
    private val preprocessor: PhotoPreprocessor,
    private val verifyPhoto: VerifyPhotoUseCase,
    private val photoStore: PhotoStore,
    private val speaker: Speaker,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(itemId: Int): VerifyViewModel
    }

    /** The item being looked for, shown on screen and sent to the model. */
    val itemText: String = session.state.value?.items?.firstOrNull { it.id == itemId }?.text.orEmpty()

    private val _state = MutableStateFlow<VerifyUiState>(VerifyUiState.Capturing)
    val state: StateFlow<VerifyUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        speaker.stop() // quiet while the camera is open
    }

    /** Called with the raw camera shot. It is shrunk in memory, checked on the phone and never uploaded. */
    fun onPhotoCaptured(raw: ByteArray, rotationDegrees: Int) {
        job?.cancel()
        _state.value = VerifyUiState.Checking(photo = null)
        job = viewModelScope.launch {
            _state.value = try {
                val photo = preprocessor.prepare(raw, rotationDegrees)
                _state.value = VerifyUiState.Checking(photo)
                val verification = verifyPhoto(itemText, photo)
                if (verification.outcome == VerificationOutcome.MATCH) {
                    session.markFound(itemId, photoStore.save(itemId, photo))
                }
                speaker.speak(listOfNotNull(verification.message, verification.hint).joinToString(" "))
                VerifyUiState.Result(photo, verification)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelNotAvailableException) {
                VerifyUiState.Failed(FailureReason.MODEL_MISSING)
            } catch (e: Exception) {
                VerifyUiState.Failed(FailureReason.GENERIC)
            }
        }
    }

    /** Stops checking and goes back to the camera. */
    fun cancel() {
        job?.cancel()
        speaker.stop()
        _state.value = VerifyUiState.Capturing
    }

    /** Back to the camera for another try. */
    fun retake() {
        job?.cancel()
        speaker.stop()
        _state.value = VerifyUiState.Capturing
    }

    override fun onCleared() {
        speaker.stop()
    }
}
