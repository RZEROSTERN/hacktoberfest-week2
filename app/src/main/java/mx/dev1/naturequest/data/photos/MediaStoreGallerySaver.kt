package mx.dev1.naturequest.data.photos

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.dev1.naturequest.domain.hunt.GallerySaver
import java.io.File
import javax.inject.Inject

/**
 * Copies photos into Pictures/Nature Quest through MediaStore. No storage permission is needed to
 * add our own files. Only what the player chose to save leaves the app's private cache.
 */
class MediaStoreGallerySaver @Inject constructor(
    @ApplicationContext private val context: Context,
) : GallerySaver {
    override suspend fun save(photoPaths: List<String>): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val stamp = System.currentTimeMillis()
        var saved = 0
        photoPaths.forEachIndexed { index, path ->
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "NatureQuest-$stamp-${index + 1}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Nature Quest")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } }
                        ?: error("Could not open $uri")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    saved++
                } catch (e: Exception) {
                    resolver.delete(uri, null, null) // do not leave a half-written file in the gallery
                }
            }
        }
        saved
    }
}
