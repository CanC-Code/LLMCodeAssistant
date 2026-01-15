package io.canccode.aca

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"
        private const val MODEL_URL = "https://huggingface.co/meta-llama/Llama-2-3b-chat-hf/resolve/main/ggml-model-q4_0.bin"
        private const val MODEL_FILENAME = "llama-3b-q4_0.bin"
    }

    private var initialized = false

    /**
     * Initialize the LLM. Downloads model if missing.
     * Progress callback: 0..100
     */
    suspend fun initialize(progressCallback: ((Int) -> Unit)? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelFile = File(context.filesDir, MODEL_FILENAME)

                if (!modelFile.exists()) {
                    Log.i(TAG, "Downloading model to ${modelFile.absolutePath}")
                    downloadModel(modelFile, progressCallback)
                } else {
                    Log.i(TAG, "Model already exists: ${modelFile.absolutePath}")
                }

                val ok = loadModelDefault(modelFile.absolutePath)
                if (ok) {
                    initialized = true
                }
                ok
            } catch (e: Exception) {
                Log.e(TAG, "LLM initialization failed", e)
                false
            }
        }
    }

    /**
     * Load model with default context size and threads
     */
    fun loadModelDefault(path: String): Boolean {
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val nCtx = 2048
        return LlamaJNI.loadModel(path, nCtx, threads)
    }

    /**
     * Generate text
     */
    fun infer(prompt: String): String {
        if (!initialized) return "LLM not initialized"
        return LlamaJNI.generateText(prompt)
    }

    /**
     * Free model from memory
     */
    fun close() {
        if (initialized) {
            LlamaJNI.freeModel()
            initialized = false
        }
    }

    /**
     * Download model from MODEL_URL
     */
    private fun downloadModel(file: File, progressCallback: ((Int) -> Unit)?) {
        val url = java.net.URL(MODEL_URL)
        url.openConnection().apply {
            connect()
            val total = contentLength
            url.getInputStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var downloaded = 0
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val percent = (downloaded * 100 / total)
                        progressCallback?.invoke(percent.coerceAtMost(100))
                    }
                }
            }
        }
    }
}