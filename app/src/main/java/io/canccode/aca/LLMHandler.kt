package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Handles model lifecycle management. 
 * Refactored to support SAF (Storage Access Framework) instead of downloads.
 */
class LLMHandler(private val context: Context) {

    private val TAG = "LLMHandler"
    
    // The directory where we will store a temporary copy of the selected GGUF
    private val modelCacheDir: File by lazy {
        File(context.cacheDir, "llm_models").also { it.mkdirs() }
    }

    /**
     * Prepares a model selected via SAF for the native layer.
     * @param uri The URI returned by the Android File Picker.
     * @return The absolute path to the local copy, or null if failed.
     */
    fun prepareModelFromUri(uri: Uri): String? {
        try {
            // Create a temporary file name
            val localFile = File(modelCacheDir, "loaded_model.gguf")
            
            Log.i(TAG, "Importing model from URI to: ${localFile.absolutePath}")

            // Open the SAF stream and copy it to internal storage
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Model import successful.")
            return localFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model from SAF", e)
            return null
        }
    }

    /**
     * Initializes the LLM with the provided path.
     */
    fun initializeModel(path: String, contextSize: Int = 2048): Boolean {
        Log.i(TAG, "Initializing LlamaBridge with context: $contextSize")
        return LlamaBridge.init(path, contextSize)
    }

    /**
     * Cleans up the cached model to save storage space.
     */
    fun clearModelCache() {
        modelCacheDir.deleteRecursively()
        Log.i(TAG, "Model cache cleared.")
    }
}
