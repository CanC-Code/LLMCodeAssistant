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
        private const val MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"

        // DEV MODE ONLY — replace with real hash before release
        private const val MODEL_SHA256 =
            "0000000000000000000000000000000000000000000000000000000000000000"

        private const val CTX_SIZE = 2048
    }

    private val downloader = ModelDownloader(context)
    private var isInitialized = false

    suspend fun initialize(onProgress: (Int) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {

            if (isInitialized) return@withContext true

            val modelFile =
                downloader.getModel(MODEL_NAME)
                    ?: downloader.downloadModel(
                        modelUrl = MODEL_URL,
                        outputName = MODEL_NAME,
                        expectedSha256 = MODEL_SHA256,
                        onProgress = onProgress
                    )

            Log.i(TAG, "Loading model: ${modelFile.absolutePath}")

            val success = LlamaBridge.initNative(modelFile.absolutePath, CTX_SIZE)
            isInitialized = success

            if (!success) {
                Log.e(TAG, "Native initialization failed")
            }

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