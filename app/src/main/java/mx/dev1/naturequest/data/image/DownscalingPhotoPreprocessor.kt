package mx.dev1.naturequest.data.image

import android.graphics.BitmapFactory
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.dev1.naturequest.domain.verification.PhotoPreprocessor
import java.io.ByteArrayInputStream
import javax.inject.Inject

/** Shrinks camera photos to [MAX_SIDE_PX] and straightens them, all in memory. */
class DownscalingPhotoPreprocessor @Inject constructor() : PhotoPreprocessor {
    override suspend fun prepare(raw: ByteArray, rotationDegrees: Int): ByteArray =
        withContext(Dispatchers.Default) {
            // A JPEG that already carries its rotation in EXIF is straightened by the decoder;
            // rotating it again would turn it the wrong way.
            val exifRotates = exifOrientation(raw) !in setOf(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL)
            val prepared = ImageDownscaler.toJpeg(raw, MAX_SIDE_PX, rotationDegrees = if (exifRotates) 0 else rotationDegrees)
            Log.d(
                TAG,
                "raw ${raw.size / 1024} KB ${size(raw)}, cameraRotation=$rotationDegrees, exifRotates=$exifRotates " +
                    "-> ${prepared.size / 1024} KB ${size(prepared)}",
            )
            prepared
        }

    private fun size(jpeg: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        return "${bounds.outWidth}x${bounds.outHeight}"
    }

    private fun exifOrientation(jpeg: ByteArray): Int =
        runCatching {
            ExifInterface(ByteArrayInputStream(jpeg))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

    private companion object {
        const val TAG = "NQ_PHOTO"

        /** Chosen in the model spike: 140 visual tokens at this size verify in about 5 s (docs/BENCHMARKS.md). */
        const val MAX_SIDE_PX = 640
    }
}
