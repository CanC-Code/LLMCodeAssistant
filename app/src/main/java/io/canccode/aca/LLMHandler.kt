package io.canccode.aca

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"
        private const val MODEL_NAME = "ggml-llama-2-3b-q4_0.bin"
        private const val ASSET_MODEL_PATH = "models/$MODEL_NAME"
    }

    private var initialized = false
    private var modelFile: File? = null

    /**
     * Initialize the LLM. Copies the model from assets to app files if necessary.
     */
    @Synchronized
    fun initialize(): Boolean {
        if (initialized) return true

        // Ensure app files path for LLM models exists
        val modelDir = File(context.filesDir, "llm")
        if (!modelDir.exists()) modelDir.mkdirs()

        val destFile = File(modelDir, MODEL_NAME)
        modelFile = destFile

        // Copy from assets if not already present
        if (!destFile.exists()) {
            try {
                context.assets.open(ASSET_MODEL_PATH).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Copied model from assets to ${destFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model from assets", e)
                return false
            }
        } else {
            Log.i(TAG, "Model already exists at ${destFile.absolutePath}")
        }

        // Load model via JNI
        initialized = LlamaJNI.loadModel(destFile.absolutePath)
        if (initialized) {
            Log.i(TAG, "LLM initialized successfully with model: $MODEL_NAME")
        } else {
            Log.e(TAG, "Failed to initialize LLM")
        }

        return initialized
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Run a prompt through the model.
     */
    fun infer(prompt: String): String {
        if (!initialized) return "LLM not initialized"
        return LlamaJNI.generateText(prompt)
    }

    @Synchronized
    fun close() {
        if (!initialized) return
        LlamaJNI.freeModel()
        initialized = false
        Log.i(TAG, "LLM closed")
    }
}