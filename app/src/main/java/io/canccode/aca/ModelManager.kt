package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages user-provided GGUF models via Storage Access Framework.
 * Optimized for Qwen2.5-Coder and other GGUF models imported by the user.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_DIR = "models"
        private const val PREF_NAME = "model_prefs"
        private const val KEY_CURRENT_MODEL = "current_model_name"
    }

    private val modelDir = File(context.filesDir, MODEL_DIR)

    init {
        if (!modelDir.exists()) modelDir.mkdirs()
    }

    /**
     * Returns the absolute path of the last used model, or the first one found in internal storage.
     */
    fun getModelPath(): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_CURRENT_MODEL, null)

        val file = if (savedName != null) File(modelDir, savedName) else null

        return if (file != null && file.exists()) {
            file.absolutePath
        } else {
            // Fallback: list files and pick the first GGUF found
            val firstModel = modelDir.listFiles { f -> f.extension == "gguf" }?.firstOrNull()
            if (firstModel != null) {
                // Update prefs so fallback is remembered
                prefs.edit().putString(KEY_CURRENT_MODEL, firstModel.name).apply()
                firstModel.absolutePath
            } else null
        }
    }

    /**
     * Returns a display name for the current model.
     */
    fun getModelDisplayName(): String {
        val path = getModelPath() ?: return "No Model Loaded"
        return File(path).name
    }

    /**
     * Imports a model from a SAF Uri (content://) into internal app storage.
     * This is required because the JNI layer needs a direct filesystem path.
     */
    suspend fun importModelFromUri(uri: Uri, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val fileName = getFileNameFromUri(uri)
                val destinationFile = File(modelDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    // Attempt to get the actual size for accurate progress
                    val totalSize = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        it.statSize
                    } ?: -1L

                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(1024 * 1024) // 1MB buffer
                        var bytesCopied: Long = 0
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read

                            if (totalSize > 0) {
                                val progress = (bytesCopied * 100 / totalSize).toInt()
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }

                // Persist the selection
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CURRENT_MODEL, fileName)
                    .apply()

                Log.i(TAG, "Import successful: $fileName")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import model: ${e.message}")
                false
            }
        }

    /**
     * Deletes all imported models to reclaim disk space.
     */
    fun clearAllModels() {
        modelDir.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Extracts the real filename from a SAF Uri using ContentResolver.
     */
    private fun getFileNameFromUri(uri: Uri): String {
        var name = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name.ifEmpty { uri.path?.substringAfterLast('/') ?: "model.gguf" }
    }
}
