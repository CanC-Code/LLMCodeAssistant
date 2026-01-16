// File: app/src/main/java/io/canccode/aca/LLMHandler.kt
package io.canccode.aca

import android.content.Context
import android.util.Log
import com.llmassistant.utils.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"
        private const val MODEL_NAME = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        // TODO: Replace with actual SHA256 hash of your model
        private const val MODEL_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000"
        private const val CTX_SIZE = 2048
    }

    private val downloader = ModelDownloader(context)
    private var isInitialized = false

    suspend fun initialize(onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                Log.i(TAG, "Already initialized")
                return@withContext true
            }

            // Check if model exists, download if not
            var modelFile = downloader.getModel(MODEL_NAME)
            if (modelFile == null) {
                Log.i(TAG, "Model not found, downloading...")
                modelFile = downloader.downloadModel(
                    modelUrl = MODEL_URL,
                    outputName = MODEL_NAME,
                    expectedSha256 = MODEL_SHA256,
                    onProgress = onProgress
                )
            } else {
                Log.i(TAG, "Model found at ${modelFile.absolutePath}")
            }

            // Load model into native memory
            Log.i(TAG, "Loading model into memory...")
            val success = LlamaBridge.initNative(modelFile.absolutePath, CTX_SIZE)
            
            if (success) {
                isInitialized = true
                Log.i(TAG, "Model loaded successfully")
            } else {
                Log.e(TAG, "Failed to load model")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialization", e)
            false
        }
    }

    fun infer(prompt: String, maxTokens: Int = 100): String {
        if (!isInitialized) {
            return "[Error: LLM not initialized]"
        }
        return try {
            LlamaBridge.generateNative(prompt, maxTokens)
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            "[Error: ${e.message}]"
        }
    }

    fun close() {
        if (isInitialized) {
            LlamaBridge.shutdownNative()
            isInitialized = false
            Log.i(TAG, "Model closed")
        }
    }
}