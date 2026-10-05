package mx.dev1.naturequest.data.image

import android.graphics.Bitmap
import android.graphics.ImageDecoder
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
     * Decodes [source] (any format Android supports), applies its EXIF rotation, scales the longest
     * side down to at most [maxSidePx] and returns JPEG bytes.
     */
    fun toJpeg(source: ByteArray, maxSidePx: Int, quality: Int = 85): ByteArray {
        require(maxSidePx > 0) { "maxSidePx must be positive" }
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(source))) { decoder, info, _ ->
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
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
