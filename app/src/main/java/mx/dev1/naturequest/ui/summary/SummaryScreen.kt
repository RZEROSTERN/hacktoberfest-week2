package mx.dev1.naturequest.ui.summary

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.hunt.Medal
import mx.dev1.naturequest.ui.theme.MedalBronze
import mx.dev1.naturequest.ui.theme.MedalGold
import mx.dev1.naturequest.ui.theme.MedalSilver

@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // System back closes the summary like the Done button (the ViewModel then deletes the photo cache).
    BackHandler(onBack = onDone)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = state) {
            SummaryUiState.Empty -> EmptyContent(onDone)
            is SummaryUiState.Ready -> ReadyContent(current, onSave = viewModel::saveToGallery, onDone = onDone)
        }
    }
}

@Composable
private fun EmptyContent(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.summary_empty), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(stringResource(R.string.summary_done))
        }
    }
}

@Composable
private fun ReadyContent(state: SummaryUiState.Ready, onSave: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SummaryHeader(state) }
            items(state.items) { item -> SummaryRow(item) }
        }
        SaveButton(state, onSave)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(stringResource(R.string.summary_done))
        }
    }
}

@Composable
private fun SummaryHeader(state: SummaryUiState.Ready) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.summary_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Surface(shape = CircleShape, color = medalColor(state.medal), modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = state.medalName,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Text(state.medalName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(state.foundText, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(state.timeText, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SummaryRow(item: SummaryItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoThumb(item.photoPath)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.text, style = MaterialTheme.typography.bodyLarge)
                if (!item.found) {
                    Text(
                        stringResource(R.string.summary_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.found) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.hunt_item_found))
            }
        }
    }
}

@Composable
private fun PhotoThumb(photoPath: String?) {
    val bitmap = remember(photoPath) {
        photoPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.verify_photo_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SaveButton(state: SummaryUiState.Ready, onSave: () -> Unit) {
    if (state.photoPaths.isEmpty()) return
    when (val save = state.save) {
        is SaveState.Saved -> Text(
            text = pluralStringResource(R.plurals.summary_saved, save.count, save.count),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        else -> {
            if (save is SaveState.Failed) {
                Text(
                    stringResource(R.string.summary_save_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(
                onClick = onSave,
                enabled = save !is SaveState.Saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) {
                Text(stringResource(R.string.summary_save))
            }
        }
    }
}

private fun medalColor(medal: Medal): Color = when (medal) {
    Medal.GOLD -> MedalGold
    Medal.SILVER -> MedalSilver
    Medal.BRONZE -> MedalBronze
}
