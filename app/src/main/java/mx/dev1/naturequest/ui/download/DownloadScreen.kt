package mx.dev1.naturequest.ui.download

import android.text.format.Formatter
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.model.DownloadFailureReason

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val size = { bytes: Long -> Formatter.formatFileSize(context, bytes) }

    // A 2.6 GB download must not be interrupted by the screen turning off.
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            when (val current = state) {
                is DownloadUiState.Idle -> {
                    Intro(size(current.totalBytes), metered = current.metered)
                    if (current.partialBytes > 0) {
                        Text(stringResource(R.string.download_partial, size(current.partialBytes)), style = MaterialTheme.typography.bodyLarge)
                    }
                    StartButton(resume = current.partialBytes > 0, onClick = viewModel::start)
                    BackButton(onBack)
                }
                is DownloadUiState.Downloading -> {
                    Text(stringResource(R.string.download_downloading), style = MaterialTheme.typography.headlineMedium)
                    ProgressBlock(current.downloaded, current.total, size)
                    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                is DownloadUiState.Verifying -> {
                    Text(stringResource(R.string.download_verifying), style = MaterialTheme.typography.headlineMedium)
                    ProgressBlock(current.checked, current.total, size)
                }
                is DownloadUiState.Failed -> {
                    Text(stringResource(failureMessage(current.reason)), style = MaterialTheme.typography.headlineMedium)
                    if (current.reason == DownloadFailureReason.NETWORK && current.partialBytes > 0) {
                        Text(stringResource(R.string.download_partial, size(current.partialBytes)), style = MaterialTheme.typography.bodyLarge)
                    }
                    StartButton(resume = current.partialBytes > 0, onClick = viewModel::start)
                    BackButton(onBack)
                }
                DownloadUiState.Done -> {
                    Text(stringResource(R.string.download_done_title), style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        Text(stringResource(R.string.download_done_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro(totalSize: String, metered: Boolean) {
    Text(stringResource(R.string.download_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.download_body, totalSize), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.download_wifi), style = MaterialTheme.typography.bodyLarge)
    if (metered) {
        Text(
            text = stringResource(R.string.download_metered),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ProgressBlock(done: Long, total: Long, size: (Long) -> String) {
    val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
    Text(
        text = stringResource(R.string.download_progress, size(done), size(total), (fraction * 100).toInt()),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun StartButton(resume: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Text(stringResource(if (resume) R.string.download_continue else R.string.download_start))
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Text(stringResource(R.string.action_back))
    }
}

private fun failureMessage(reason: DownloadFailureReason): Int = when (reason) {
    DownloadFailureReason.NOT_ENOUGH_SPACE -> R.string.download_error_space
    DownloadFailureReason.NETWORK -> R.string.download_error_network
    DownloadFailureReason.SERVER -> R.string.download_error_server
    DownloadFailureReason.CORRUPT -> R.string.download_error_corrupt
}
