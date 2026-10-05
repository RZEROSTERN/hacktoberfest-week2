package mx.dev1.naturequest.debug

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class SpikeViewModel(application: Application) : AndroidViewModel(application) {
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private var job: Job? = null

    fun run(config: SpikeConfig) {
        if (job?.isActive == true) return
        _log.value = emptyList()
        job = viewModelScope.launch {
            _running.value = true
            try {
                SpikeRunner(getApplication(), ::append).run(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Errors too (for example ExceptionInInitializerError): show them instead of crashing.
                append("FAILED: ${e::class.simpleName}: ${e.message} ${e.cause?.message.orEmpty()}")
            } finally {
                _running.value = false
                append("Done.")
                Log.i(SpikeRunner.TAG, "Done.") // lets adb scripts know the run is over
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun append(line: String) = _log.update { (it + line).takeLast(MAX_LINES) }

    private companion object {
        const val MAX_LINES = 400
    }
}
