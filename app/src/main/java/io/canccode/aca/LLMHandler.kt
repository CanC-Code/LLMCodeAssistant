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
        private const val MODEL_NAME = "model.gguf"
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val DEV_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
        private const val CTX_SIZE = 512
    }

    private val downloader = ModelDownloader(context)
    private var isInitialized = false

    suspend fun initialize(onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        val modelFile = downloader.getModel(MODEL_NAME)
            ?: downloader.downloadModel(MODEL_URL, MODEL_NAME, DEV_HASH, onProgress)

        Log.i(TAG, "Loading model: ${modelFile.absolutePath}")
        val success = LlamaBridge.initNative(modelFile.absolutePath, CTX_SIZE)
        isInitialized = success

        if (!success) Log.e(TAG, "Native LLM init failed")
        success
    }

    fun infer(prompt: String, maxTokens: Int = 128): String {
        if (!isInitialized) return "[LLM not initialized]"
        return LlamaBridge.generateNative(prompt, maxTokens)
    }

    fun close() {
        if (isInitialized) {
            LlamaBridge.shutdownNative()
            isInitialized = false
        }
    }
}