package mx.dev1.naturequest.data.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.dev1.naturequest.domain.model.ModelAvailability
import java.io.File
import javax.inject.Inject

/**
 * Where the on-device model lives. Development pushes it here with adb (see CLAUDE.md) and the
 * release download writes to the same place: app-private storage, never shared storage.
 */
class ModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ModelAvailability {
    val directory: File get() = context.filesDir

    fun modelFile(): File = File(directory, MODEL_FILE_NAME)

    override fun isAvailable(): Boolean = modelFile().let { it.isFile && it.length() > 0 }

    override fun partialBytes(): Long =
        File(directory, MODEL_FILE_NAME + ModelDownloader.PART_SUFFIX).takeIf { it.isFile }?.length() ?: 0L

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    }
}
