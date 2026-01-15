package io.canccode.aca

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"

        // Replace with the direct download URL for the LLaMA-2 3B model
        private const val MODEL_URL =
            "https://huggingface.co/meta-llama/Llama-2-3b-hf/resolve/main/ggml-model-q4_0.bin"

        private const val MODEL_FILENAME = "llama2-3b.bin"
    }

    private var modelFile: File = File(context.filesDir, MODEL_FILENAME)
    private var isInitialized = false

    /**
     * Initialize the LLM: download if needed, then load.
     * @param progressCallback receives progress as Int [0-100]
     * @return true if successfully initialized
     */
    suspend fun initialize(progressCallback: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelFile.exists()) {
                    Log.i(TAG, "Model not found locally. Downloading...")
                    downloadModel(MODEL_URL, modelFile, progressCallback)
                } else {
                    Log.i(TAG, "Model already exists: ${modelFile.absolutePath}")
                }

                Log.i(TAG, "Loading LLM model...")
                val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
                isInitialized = LlamaJNI.loadModel(modelFile.absolutePath, 2048, threads)

                isInitialized
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize LLM", e)
                false
            }
        }
    }

    /**
     * Generate text from a prompt using the loaded LLM.
     * @param prompt text prompt
     * @return generated text or empty string if LLM not initialized
     */
    fun generate(prompt: String): String {
        if (!isInitialized) return ""
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
        }
    }

    /**
     * Download model file from a URL with progress updates
     */
    private fun downloadModel(urlStr: String, destFile: File, progressCallback: (Int) -> Unit) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Failed to download model: ${connection.responseCode}")
        }

        val totalBytes = connection.contentLength
        var downloadedBytes = 0

        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (true) {
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = (downloadedBytes * 100 / totalBytes)
                        progressCallback(progress)
                    }
                }
                output.flush()
            }
        }
    }
}