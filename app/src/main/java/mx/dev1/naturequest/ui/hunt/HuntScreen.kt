package mx.dev1.naturequest.ui.hunt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.hunt.HuntGenerationProgress
import mx.dev1.naturequest.domain.hunt.HuntItem
import mx.dev1.naturequest.ui.theme.NatureQuestTheme

@Composable
fun HuntScreen(
    viewModel: HuntViewModel,
    onBack: () -> Unit,
    onFoundSomething: (itemId: Int) -> Unit,
    onFinished: () -> Unit,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val speaking by viewModel.speaking.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val ready = state as? HuntUiState.Ready
    val everythingFound = ready != null && ready.items.isNotEmpty() && ready.items.all { it.found }

    val finish = {
        viewModel.finish()
        onFinished()
    }
    // Found everything: the hunt is over, go straight to the medal.
    LaunchedEffect(everythingFound) { if (everythingFound) finish() }
    // Leaving a running hunt throws it away, so ask first (a stray back swipe is easy with kids).
    BackHandler(enabled = ready != null) { confirmLeave = true }

    HuntContent(
        state = state,
        speaking = speaking,
        onCancel = {
            viewModel.cancel()
            onBack()
        },
        onRetry = viewModel::generate,
        onBack = onBack,
        onFoundSomething = onFoundSomething,
        onReadAloud = viewModel::readAloud,
        onStopReading = viewModel::stopReading,
        onFinish = finish,
        onDownloadModel = onDownloadModel,
        modifier = modifier,
    )
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.hunt_leave_title)) },
            text = { Text(stringResource(R.string.hunt_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.hunt_leave_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.hunt_leave_stay)) }
            },
        )
    }
}

@Composable
private fun HuntContent(
    state: HuntUiState,
    speaking: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onFoundSomething: (Int) -> Unit,
    onReadAloud: () -> Unit,
    onStopReading: () -> Unit,
    onFinish: () -> Unit,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is HuntUiState.Generating -> GeneratingContent(state.progress, onCancel)
                is HuntUiState.Ready -> ReadyContent(state.items, speaking, onFoundSomething, onReadAloud, onStopReading, onFinish)
                is HuntUiState.Failed -> FailedContent(state.reason, onRetry, onBack, onDownloadModel)
            }
        }
    }
}

@Composable
private fun GeneratingContent(progress: HuntGenerationProgress, onCancel: () -> Unit) {
    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    Text(
        text = stringResource(
            when (progress) {
                HuntGenerationProgress.LOADING_MODEL -> R.string.hunt_loading_model
                HuntGenerationProgress.WRITING_LIST -> R.string.hunt_writing_list
            },
        ),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(text = stringResource(R.string.hunt_wait_hint), style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Text(text = stringResource(R.string.action_cancel))
    }
}

// Checklist: tap an item you found to photograph it; read the list aloud again; finish for the medal.
@Composable
private fun ColumnScope.ReadyContent(
    items: List<HuntItem>,
    speaking: Boolean,
    onFoundSomething: (Int) -> Unit,
    onReadAloud: () -> Unit,
    onStopReading: () -> Unit,
    onFinish: () -> Unit,
) {
    Text(text = stringResource(R.string.hunt_ready_title), style = MaterialTheme.typography.headlineMedium)
    Text(text = stringResource(R.string.hunt_tap_hint), style = MaterialTheme.typography.bodyLarge)
    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            HuntItemCard(item, onClick = { onFoundSomething(item.id) })
        }
    }
    OutlinedButton(
        onClick = if (speaking) onStopReading else onReadAloud,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Text(text = stringResource(if (speaking) R.string.hunt_stop_reading else R.string.hunt_read_aloud))
    }
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Text(text = stringResource(R.string.hunt_finish))
    }
}

@Composable
private fun HuntItemCard(item: HuntItem, onClick: () -> Unit) {
    val colors = if (item.found) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.hunt_item_numbered, item.id + 1, item.text),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.found) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            if (item.found) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.hunt_item_found))
            }
        }
    }
    if (item.found) {
        Card(modifier = Modifier.fillMaxWidth(), colors = colors) { content() }
    } else {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = colors) { content() }
    }
}

@Composable
private fun FailedContent(reason: FailureReason, onRetry: () -> Unit, onBack: () -> Unit, onDownloadModel: () -> Unit) {
    Text(
        text = stringResource(
            when (reason) {
                FailureReason.MODEL_MISSING -> R.string.hunt_error_model_missing
                FailureReason.GENERIC -> R.string.hunt_error_generic
            },
        ),
        style = MaterialTheme.typography.headlineMedium,
    )
    if (reason == FailureReason.MODEL_MISSING) {
        Button(onClick = onDownloadModel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(text = stringResource(R.string.setup_download_model))
        }
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(text = stringResource(R.string.hunt_retry))
        }
    } else {
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(text = stringResource(R.string.hunt_retry))
        }
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Text(text = stringResource(R.string.action_back))
    }
}

@Preview(showBackground = true)
@Composable
private fun HuntGeneratingPreview() {
    NatureQuestTheme {
        HuntContent(HuntUiState.Generating(HuntGenerationProgress.WRITING_LIST), false, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HuntReadyPreview() {
    NatureQuestTheme {
        HuntContent(
            HuntUiState.Ready(
                listOf(
                    HuntItem(0, "A red leaf", found = true),
                    HuntItem(1, "A feather"),
                    HuntItem(2, "Bark with moss on it"),
                ),
            ),
            false, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
