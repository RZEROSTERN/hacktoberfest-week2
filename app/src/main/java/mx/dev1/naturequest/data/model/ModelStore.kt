package mx.dev1.naturequest.data.model

import android.content.Context
import java.io.File

/**
 * Where the on-device model lives. Development pushes it here with adb (see CLAUDE.md) and the
 * release download writes to the same place: app-private storage, never shared storage.
 */
class ModelStore(private val context: Context) {
    fun modelFile(): File = File(context.filesDir, MODEL_FILE_NAME)

    fun isAvailable(): Boolean = modelFile().let { it.isFile && it.length() > 0 }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    }
}
