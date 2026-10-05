package mx.dev1.naturequest.data.photos

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.dev1.naturequest.domain.verification.PhotoStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Found photos live in the app's private cache folder, which no other app can read and the system
 * may clear at any time. They are deleted when the hunt ends and when the app starts.
 */
@Singleton
class CachePhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoStore {
    private val directory get() = File(context.cacheDir, DIRECTORY_NAME)

    override suspend fun save(itemId: Int, jpeg: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            File(directory, "item-$itemId.jpg").also { it.writeBytes(jpeg) }.absolutePath
        }.getOrNull()
    }

    override fun clear() {
        directory.deleteRecursively()
    }

    private companion object {
        const val DIRECTORY_NAME = "hunt-photos"
    }
}
