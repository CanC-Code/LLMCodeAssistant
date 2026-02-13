package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages user-provided GGUF models via Storage Access Framework.
 * No hardcoded downloads; relies on the user to provide the model file.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_DIR = "models"
        
        // Key for SharedPreferences to remember the last used model name
        private const val PREF_NAME = "model_prefs"
        private const val KEY_CURRENT_MODEL = "current_model_name"
    }

    private val modelDir = File(context.filesDir, MODEL_DIR)

    init {
        if (!modelDir.exists()) modelDir.mkdirs()
    }

    /**
     * Checks if any model is currently available in the internal storage.
     */
    fun hasAnyModel(): Boolean {
        return modelDir.listFiles { f -> f.extension == "gguf" }?.isNotEmpty() ?: false
    }

    /**
     * Returns the absolute path of the last used model, or the first one found.
     */
    fun getModelPath(): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_CURRENT_MODEL, null)
        
        val file = if (savedName != null) File(modelDir, savedName) else null
        
        return if (file != null && file.exists()) {
            file.absolutePath
        } else {
            // Fallback: list files and pick the first GGUF
            val firstModel = modelDir.listFiles { f -> f.extension == "gguf" }?.firstOrNull()
            firstModel?.absolutePath
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
     * Imports a model from a SAF Uri (content://) into internal storage.
     * llama_jni requires a real file path, which SAF doesn't provide directly.
     */
    suspend fun importModelFromUri(uri: Uri, onProgress: (Int) -> Unit): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                val fileName = getFileName(uri) ?: "imported_model.gguf"
                val destinationFile = File(modelDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    val totalSize = input.available().toLong() // Note: may not be accurate for large files
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(1024 * 1024) // 1MB buffer for speed
                        var bytesCopied: Long = 0
                        var read: Int
                        
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            
                            // Approximate progress if size is known
                            if (totalSize > 0) {
                                val progress = (bytesCopied * 100 / totalSize).toInt()
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }

                // Save this as the current model
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CURRENT_MODEL, fileName)
                    .apply()

                Log.i(TAG, "Successfully imported model: $fileName")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import model from SAF", e)
                false
            }
        }

    /**
     * Clears all imported models to save space.
     */
    fun clearAllModels() {
        modelDir.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun getFileName(uri: Uri): String? {
        return uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        } ?: "model.gguf"
    }
}
