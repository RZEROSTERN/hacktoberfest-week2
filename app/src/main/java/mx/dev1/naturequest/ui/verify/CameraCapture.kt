package mx.dev1.naturequest.ui.verify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import mx.dev1.naturequest.R
import java.io.ByteArrayOutputStream

/**
 * Live camera preview with a big shutter button. The photo is delivered in memory (never written
 * to disk here) together with the rotation needed to straighten it.
 *
 * [overlay] is drawn on top of the preview (for example a "looking for…" banner).
 */
@Composable
fun CameraCapture(
    onPhotoCaptured: (jpeg: ByteArray, rotationDegrees: Int) -> Unit,
    onCameraFailure: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            cameraProvider = provider
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the camera", e)
            onCameraFailure()
        }
    }
    // Free the camera as soon as this screen leaves, not only when the activity is destroyed.
    DisposableEffect(cameraProvider) {
        // Capture the value now: reading the state in onDispose would see the NEW provider when this
        // effect is replaced, and unbind the camera that was just bound.
        val provider = cameraProvider
        onDispose { provider?.unbindAll() }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        overlay()
        ShutterButton(
            enabled = cameraProvider != null && !capturing,
            onClick = {
                capturing = true
                imageCapture.targetRotation = previewView.display?.rotation ?: imageCapture.targetRotation
                takePhoto(
                    imageCapture = imageCapture,
                    context = context,
                    onSuccess = { jpeg, rotation ->
                        capturing = false
                        onPhotoCaptured(jpeg, rotation)
                    },
                    onFailure = {
                        capturing = false
                        onCameraFailure()
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(88.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .background(if (enabled) Color.White else Color.Gray, CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = stringResource(R.string.verify_take_photo),
                onClick = onClick,
            ),
    )
}

private const val TAG = "NQ_PHOTO"

private fun takePhoto(
    imageCapture: ImageCapture,
    context: Context,
    onSuccess: (ByteArray, Int) -> Unit,
    onFailure: () -> Unit,
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                Log.d(TAG, "captured format=${image.format} ${image.width}x${image.height} rotation=$rotation")
                val jpeg = try {
                    image.toJpegBytes()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read the captured image", e)
                    null
                } finally {
                    image.close()
                }
                if (jpeg != null) onSuccess(jpeg, rotation) else onFailure()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                onFailure()
            }
        },
    )
}

/** JPEG bytes of the capture, whether CameraX delivered a JPEG buffer or raw YUV. */
private fun ImageProxy.toJpegBytes(): ByteArray =
    if (format == ImageFormat.JPEG) {
        planes[0].buffer.let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }
    } else {
        ByteArrayOutputStream().use { out ->
            toBitmap().compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }
