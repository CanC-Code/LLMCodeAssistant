package io.canccode.aca

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"

        // Direct download URL for LLaMA-2 3B model (ggml quantized)
        private const val MODEL_URL =
            "https://huggingface.co/meta-llama/Llama-2-3b-hf/resolve/main/ggml-model-q4_0.bin"

        private const val MODEL_FILENAME = "llama2-3b.bin"
    }

    private val modelFile: File = File(context.filesDir, MODEL_FILENAME)
    @Volatile
    private var isInitialized = false

    /**
     * Initialize the LLM: download if needed, then load.
     * @param progressCallback receives progress as Int [0-100]
     * @return true if successfully initialized
     */
    suspend fun initialize(progressCallback: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.exists()) {
                Log.i(TAG, "Model not found locally. Downloading...")
                downloadModel(MODEL_URL, modelFile, progressCallback)
            } else {
                Log.i(TAG, "Model already exists: ${modelFile.absolutePath}")
                progressCallback(100)
            }

            Log.i(TAG, "Loading LLM model...")
            val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            isInitialized = LlamaJNI.loadModel(modelFile.absolutePath, 2048, threads)

            if (isInitialized) Log.i(TAG, "LLM initialized successfully")
            else Log.e(TAG, "LLM failed to initialize")

            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            false
        }
    }

    /**
     * Generate text from a prompt using the loaded LLM.
     * @param prompt text prompt
     * @return generated text or empty string if LLM not initialized
     */
    fun generate(prompt: String): String {
        if (!isInitialized) {
            Log.w(TAG, "LLM not initialized; cannot generate text")
            return ""
        }
        return try {
            LlamaJNI.generateText(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating text", e)
            ""
        }
    }

    /**
     * Free the model from memory
     */
    fun close() {
        if (isInitialized) {
            LlamaJNI.freeModel()
            isInitialized = false
            Log.i(TAG, "LLM freed from memory")
        }
    }

    /**
     * Download model file from a URL with progress updates
     */
    private suspend fun downloadModel(
        urlStr: String,
        destFile: File,
        progressCallback: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            connect()
        }

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Failed to download model: HTTP ${connection.responseCode}")
        }

        val totalBytes = connection.contentLength
        var downloadedBytes = 0

        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = (downloadedBytes * 100 / totalBytes)
                        withContext(Dispatchers.Main) { progressCallback(progress.coerceIn(0, 100)) }
                    }
                }
                output.flush()
            }
        }
        Log.i(TAG, "Model downloaded successfully: ${destFile.absolutePath}")
    }
}