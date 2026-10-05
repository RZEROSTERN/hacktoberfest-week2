package mx.dev1.naturequest.ui.verify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.verification.PhotoVerification
import mx.dev1.naturequest.domain.verification.VerificationOutcome
import mx.dev1.naturequest.ui.hunt.FailureReason

@Composable
fun VerifyScreen(
    viewModel: VerifyViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = state) {
            VerifyUiState.Capturing -> CapturingContent(
                itemText = viewModel.itemText,
                onPhotoCaptured = viewModel::onPhotoCaptured,
                onBack = onBack,
            )
            is VerifyUiState.Checking -> CheckingContent(current.photo, onCancel = viewModel::cancel)
            is VerifyUiState.Result -> ResultContent(
                photo = current.photo,
                verification = current.verification,
                onTryAgain = viewModel::retake,
                onBack = onBack,
            )
            is VerifyUiState.Failed -> FailedContent(current.reason, onRetry = viewModel::retake, onBack = onBack)
        }
    }
}

@Composable
private fun CapturingContent(
    itemText: String,
    onPhotoCaptured: (ByteArray, Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        denied = !isGranted
    }
    // The player may grant the permission in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCameraPermission(context) }

    when {
        !granted -> PermissionContent(
            denied = denied,
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onBack = onBack,
        )
        cameraFailed -> MessageContent(
            title = stringResource(R.string.verify_camera_error),
            primaryLabel = stringResource(R.string.verify_try_again),
            onPrimary = { cameraFailed = false },
            onBack = onBack,
        )
        else -> CameraCapture(
            onPhotoCaptured = onPhotoCaptured,
            onCameraFailure = { cameraFailed = true },
        ) {
            LookingForBanner(itemText, onBack)
        }
    }
}

@Composable
private fun BoxScope.LookingForBanner(itemText: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .safeDrawingPadding()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.verify_looking_for, itemText),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@Composable
private fun PermissionContent(
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.verify_permission_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.verify_permission_body), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(stringResource(R.string.verify_permission_allow))
        }
        if (denied) {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.verify_permission_settings))
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun CheckingContent(photo: ByteArray?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhotoThumbnail(photo)
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.verify_checking),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.verify_checking_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun ResultContent(
    photo: ByteArray,
    verification: PhotoVerification,
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val matched = verification.outcome == VerificationOutcome.MATCH
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhotoThumbnail(photo)
        Text(
            text = stringResource(
                when (verification.outcome) {
                    VerificationOutcome.MATCH -> R.string.verify_result_match
                    VerificationOutcome.NO_MATCH -> R.string.verify_result_no_match
                    VerificationOutcome.NOT_SURE -> R.string.verify_result_not_sure
                },
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(text = verification.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        verification.hint?.let { hint ->
            Text(
                text = stringResource(R.string.verify_hint, hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        if (matched) {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.verify_back_to_list))
            }
        } else {
            Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.verify_try_again))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.verify_back_to_list))
            }
        }
    }
}

@Composable
private fun FailedContent(reason: FailureReason, onRetry: () -> Unit, onBack: () -> Unit) {
    MessageContent(
        title = stringResource(
            when (reason) {
                FailureReason.MODEL_MISSING -> R.string.hunt_error_model_missing
                FailureReason.GENERIC -> R.string.verify_error_generic
            },
        ),
        primaryLabel = stringResource(R.string.verify_try_again),
        onPrimary = onRetry,
        onBack = onBack,
    )
}

@Composable
private fun MessageContent(title: String, primaryLabel: String, onPrimary: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { Text(primaryLabel) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: ByteArray?) {
    val bitmap = remember(photo) { photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(
        modifier = Modifier
            .size(width = 240.dp, height = 240.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
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

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
