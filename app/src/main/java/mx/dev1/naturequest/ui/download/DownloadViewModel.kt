package mx.dev1.naturequest.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.dev1.naturequest.domain.model.DownloadFailure
import mx.dev1.naturequest.domain.model.DownloadFailureReason
import mx.dev1.naturequest.domain.model.DownloadProgress
import mx.dev1.naturequest.domain.model.ModelAvailability
import mx.dev1.naturequest.domain.model.ModelDownloading
import mx.dev1.naturequest.domain.model.NetworkInfo
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface DownloadUiState {
    /** Nothing running. [partialBytes] > 0 means an interrupted download can be continued. */
    data class Idle(val totalBytes: Long, val partialBytes: Long, val metered: Boolean) : DownloadUiState

    data class Downloading(val downloaded: Long, val total: Long) : DownloadUiState

    data class Verifying(val checked: Long, val total: Long) : DownloadUiState

    data class Failed(val reason: DownloadFailureReason, val partialBytes: Long, val totalBytes: Long) : DownloadUiState

    data object Done : DownloadUiState
}

/**
 * Runs the one-time model download while this screen is open. Leaving the screen (or Cancel) stops
 * it and keeps what was downloaded, so the next start continues from there.
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloader: ModelDownloading,
    private val availability: ModelAvailability,
    private val network: NetworkInfo,
) : ViewModel() {

    private val _state = MutableStateFlow<DownloadUiState>(if (availability.isAvailable()) DownloadUiState.Done else idle())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = DownloadUiState.Downloading(availability.partialBytes(), downloader.totalBytes)
            try {
                downloader.download().collect { progress ->
                    _state.value = when (progress) {
                        is DownloadProgress.Downloading -> DownloadUiState.Downloading(progress.downloaded, progress.total)
                        is DownloadProgress.Verifying -> DownloadUiState.Verifying(progress.checked, progress.total)
                        DownloadProgress.Done -> DownloadUiState.Done
                    }
                }
            } catch (e: CancellationException) {
                _state.value = idle()
                throw e
            } catch (e: DownloadFailure) {
                _state.value = DownloadUiState.Failed(e.reason, availability.partialBytes(), downloader.totalBytes)
            } catch (e: Exception) {
                _state.value = DownloadUiState.Failed(DownloadFailureReason.NETWORK, availability.partialBytes(), downloader.totalBytes)
            }
        }
    }

    /** Stops the download and keeps the partial file. */
    fun cancel() {
        job?.cancel()
    }

    private fun idle() = DownloadUiState.Idle(downloader.totalBytes, availability.partialBytes(), network.isMetered())
}
