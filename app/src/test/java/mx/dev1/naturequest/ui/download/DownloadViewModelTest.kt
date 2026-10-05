package mx.dev1.naturequest.ui.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.domain.model.DownloadFailure
import mx.dev1.naturequest.domain.model.DownloadFailureReason
import mx.dev1.naturequest.domain.model.DownloadProgress
import mx.dev1.naturequest.domain.model.ModelAvailability
import mx.dev1.naturequest.domain.model.ModelDownloading
import mx.dev1.naturequest.domain.model.NetworkInfo
import mx.dev1.naturequest.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DownloadViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeAvailability(var available: Boolean = false, var partial: Long = 0) : ModelAvailability {
        override fun isAvailable() = available

        override fun partialBytes() = partial
    }

    private class FakeDownloader(private val script: () -> Flow<DownloadProgress>) : ModelDownloading {
        var starts = 0
        override val totalBytes = 1_000L

        override fun download(): Flow<DownloadProgress> {
            starts++
            return script()
        }
    }

    private class FakeNetwork(var metered: Boolean = false) : NetworkInfo {
        override fun isMetered() = metered
    }

    @Test
    fun `starts idle with the size, any partial download and the connection type`() = runTest {
        val vm = DownloadViewModel(FakeDownloader { flowOf() }, FakeAvailability(partial = 300), FakeNetwork(metered = true))

        assertEquals(DownloadUiState.Idle(totalBytes = 1_000, partialBytes = 300, metered = true), vm.state.value)
    }

    @Test
    fun `is already done when the model is on the phone`() = runTest {
        val vm = DownloadViewModel(FakeDownloader { flowOf() }, FakeAvailability(available = true), FakeNetwork())
        assertEquals(DownloadUiState.Done, vm.state.value)
    }

    @Test
    fun `shows download progress, then checking, then done`() = runTest {
        val downloader = FakeDownloader {
            flow {
                emit(DownloadProgress.Downloading(100, 1_000))
                emit(DownloadProgress.Downloading(1_000, 1_000))
                emit(DownloadProgress.Verifying(500, 1_000))
                emit(DownloadProgress.Done)
            }
        }
        val vm = DownloadViewModel(downloader, FakeAvailability(), FakeNetwork())
        val seen = mutableListOf<DownloadUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(seen) }

        vm.start()
        advanceUntilIdle()

        assertEquals(
            listOf(
                DownloadUiState.Idle(1_000, 0, false),
                DownloadUiState.Downloading(0, 1_000), // continues from what is already on disk
                DownloadUiState.Downloading(100, 1_000),
                DownloadUiState.Downloading(1_000, 1_000),
                DownloadUiState.Verifying(500, 1_000),
                DownloadUiState.Done,
            ),
            seen,
        )
    }

    @Test
    fun `reports a failure with the reason and what can be continued`() = runTest {
        val availability = FakeAvailability(partial = 400)
        val downloader = FakeDownloader { flow { throw DownloadFailure(DownloadFailureReason.CORRUPT) } }
        val vm = DownloadViewModel(downloader, availability, FakeNetwork())

        vm.start()
        advanceUntilIdle()

        assertEquals(DownloadUiState.Failed(DownloadFailureReason.CORRUPT, partialBytes = 400, totalBytes = 1_000), vm.state.value)
    }

    @Test
    fun `an unexpected error is shown as a connection problem`() = runTest {
        val vm = DownloadViewModel(FakeDownloader { flow { throw IllegalStateException("boom") } }, FakeAvailability(), FakeNetwork())

        vm.start()
        advanceUntilIdle()

        assertTrue((vm.state.value as DownloadUiState.Failed).reason == DownloadFailureReason.NETWORK)
    }

    @Test
    fun `can start again after a failure`() = runTest {
        var fail = true
        val downloader = FakeDownloader {
            flow {
                if (fail) throw DownloadFailure(DownloadFailureReason.NETWORK)
                emit(DownloadProgress.Done)
            }
        }
        val vm = DownloadViewModel(downloader, FakeAvailability(), FakeNetwork())
        vm.start()
        advanceUntilIdle()
        assertTrue(vm.state.value is DownloadUiState.Failed)

        fail = false
        vm.start()
        advanceUntilIdle()

        assertEquals(DownloadUiState.Done, vm.state.value)
        assertEquals(2, downloader.starts)
    }

    @Test
    fun `cancel stops the download and goes back to idle so it can be continued`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val availability = FakeAvailability()
        val downloader = FakeDownloader {
            flow {
                emit(DownloadProgress.Downloading(250, 1_000))
                gate.await() // the transfer is still running
                emit(DownloadProgress.Done)
            }
        }
        val vm = DownloadViewModel(downloader, availability, FakeNetwork())
        vm.start()
        advanceUntilIdle()
        assertEquals(DownloadUiState.Downloading(250, 1_000), vm.state.value)

        availability.partial = 250 // what the real downloader leaves on disk
        vm.cancel()
        advanceUntilIdle()

        assertEquals(DownloadUiState.Idle(1_000, partialBytes = 250, metered = false), vm.state.value)
    }

    @Test
    fun `starting twice does not start two downloads`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val downloader = FakeDownloader { flow { gate.await(); emit(DownloadProgress.Done) } }
        val vm = DownloadViewModel(downloader, FakeAvailability(), FakeNetwork())

        vm.start()
        advanceUntilIdle()
        vm.start()
        advanceUntilIdle()

        assertEquals(1, downloader.starts)
        gate.complete(Unit)
    }
}
