package mx.dev1.naturequest.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import mx.dev1.naturequest.ui.theme.NatureQuestTheme

@Composable
fun SetupScreen(
    onStart: (HuntSettings) -> Unit,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshModel() }
    SetupContent(
        state = state,
        onPlace = viewModel::selectPlace,
        onLength = viewModel::selectLength,
        onAge = viewModel::selectAgeRange,
        onStart = { onStart(state.toSettings()) },
        onDownloadModel = onDownloadModel,
        modifier = modifier,
    )
}

@Composable
private fun SetupContent(
    state: SetupUiState,
    onPlace: (PlaceType) -> Unit,
    onLength: (HuntLength) -> Unit,
    onAge: (AgeRange) -> Unit,
    onStart: () -> Unit,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.setup_adult_notice),
                style = MaterialTheme.typography.bodyLarge,
            )
            OptionGroup(
                title = stringResource(R.string.setup_place_title),
                options = PlaceType.entries,
                selected = state.place,
                label = { stringResource(it.labelRes()) },
                onSelect = onPlace,
                columns = 2,
            )
            OptionGroup(
                title = stringResource(R.string.setup_length_title),
                options = HuntLength.entries,
                selected = state.length,
                label = { stringResource(it.labelRes()) },
                onSelect = onLength,
                columns = 3,
            )
            OptionGroup(
                title = stringResource(R.string.setup_age_title),
                options = AgeRange.entries,
                selected = state.ageRange,
                label = { stringResource(it.labelRes()) },
                onSelect = onAge,
                columns = 3,
            )
            if (state.modelReady) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                ) {
                    Text(text = stringResource(R.string.setup_start))
                }
            } else {
                Text(text = stringResource(R.string.setup_model_needed), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = onDownloadModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                ) {
                    Text(text = stringResource(R.string.setup_download_model))
                }
            }
        }
    }
}

/** A titled group of big, mutually exclusive tiles laid out in [columns] columns. */
@Composable
private fun <T> OptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    columns: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        options.chunked(columns).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowOptions.forEach { option ->
                    OptionTile(
                        text = label(option),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep tiles the same width when the last row is not full.
                repeat(columns - rowOptions.size) { Column(modifier = Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun OptionTile(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupContentPreview() {
    NatureQuestTheme {
        SetupContent(state = SetupUiState(), onPlace = {}, onLength = {}, onAge = {}, onStart = {}, onDownloadModel = {})
    }
}
