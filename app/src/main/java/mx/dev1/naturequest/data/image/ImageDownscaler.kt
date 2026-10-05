package mx.dev1.naturequest.data.image

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a photo before it goes to the model. Phone photos are 12 MP and several MB; the model
 * only needs a small image, and a big one costs time and memory. Everything stays in memory.
 */
object ImageDownscaler {
    /**
     * Decodes [source] (any format Android supports, applying its EXIF rotation if it has one),
     * rotates it clockwise by [rotationDegrees] (for images that carry their rotation separately,
     * like CameraX captures), scales the longest side down to at most [maxSidePx] and returns JPEG
     * bytes.
     */
    fun toJpeg(source: ByteArray, maxSidePx: Int, quality: Int = 85, rotationDegrees: Int = 0): ByteArray {
        require(maxSidePx > 0) { "maxSidePx must be positive" }
        var bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(source))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSidePx) {
                val scale = maxSidePx.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        if (rotationDegrees % 360 != 0) {
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotationDegrees.toFloat()) },
                true,
            )
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
