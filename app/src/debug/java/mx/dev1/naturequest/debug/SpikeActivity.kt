package mx.dev1.naturequest.debug

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.dev1.naturequest.R
import mx.dev1.naturequest.data.inference.EngineOptions
import mx.dev1.naturequest.data.inference.InferenceBackend
import mx.dev1.naturequest.ui.theme.NatureQuestTheme

/**
 * Debug-only benchmark screen. Run it from adb without touching the phone:
 * `adb shell am start -n mx.dev1.naturequest/.debug.SpikeActivity --ez auto true --es backend CPU --ei maxSide 640 --ei budget 140`
 * Optional extras: `--es vision GPU|CPU` (image encoder backend), `--ei maxTokens 2048`.
 */
class SpikeActivity : ComponentActivity() {
    private val viewModel: SpikeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen on and visible while the benchmark runs: a locked phone throttles it.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val initial = configFrom(intent)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_AUTO, false)) {
            viewModel.run(initial)
        }
        setContent {
            NatureQuestTheme {
                val log by viewModel.log.collectAsStateWithLifecycle()
                val running by viewModel.running.collectAsStateWithLifecycle()
                SpikeScreen(
                    initial = initial,
                    log = log,
                    running = running,
                    onRun = viewModel::run,
                    onCancel = viewModel::cancel,
                )
            }
        }
    }

    private fun configFrom(intent: Intent) = SpikeConfig(
        backend = intent.getStringExtra(EXTRA_BACKEND)
            ?.let { InferenceBackend.valueOf(it.uppercase()) } ?: InferenceBackend.CPU,
        visionBackend = intent.getStringExtra(EXTRA_VISION)?.let { InferenceBackend.valueOf(it.uppercase()) },
        maxImageSidePx = intent.getIntExtra(EXTRA_MAX_SIDE, 640),
        visualTokenBudget = intent.getIntExtra(EXTRA_BUDGET, 140),
        maxNumTokens = intent.getIntExtra(EXTRA_MAX_TOKENS, 2048),
    )

    private companion object {
        const val EXTRA_AUTO = "auto"
        const val EXTRA_BACKEND = "backend"
        const val EXTRA_VISION = "vision"
        const val EXTRA_MAX_SIDE = "maxSide"
        const val EXTRA_BUDGET = "budget"
        const val EXTRA_MAX_TOKENS = "maxTokens"
    }
}

@Composable
private fun SpikeScreen(
    initial: SpikeConfig,
    log: List<String>,
    running: Boolean,
    onRun: (SpikeConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var config by remember { mutableStateOf(initial) }
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.scrollToItem(log.lastIndex)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.spike_title), style = MaterialTheme.typography.titleLarge)
            ChipRow(stringResource(R.string.spike_backend), InferenceBackend.entries, config.backend, { it.name }) {
                config = config.copy(backend = it)
            }
            ChipRow(stringResource(R.string.spike_image_side), listOf(512, 768, 1024), config.maxImageSidePx, { "$it" }) {
                config = config.copy(maxImageSidePx = it)
            }
            ChipRow(
                stringResource(R.string.spike_visual_tokens),
                EngineOptions.ALLOWED_VISUAL_TOKEN_BUDGETS, config.visualTokenBudget, { "$it" },
            ) { config = config.copy(visualTokenBudget = it) }
            if (running) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.spike_cancel))
                }
            } else {
                Button(onClick = { onRun(config) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.spike_run))
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(log) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    text: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text(option)) },
                )
            }
        }
    }
}
