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

        // Public TinyLlama GGUF
        private const val MODEL_NAME = "tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val N_CTX = 512
    }

    private val modelDir: File = File(context.filesDir, "models").apply { mkdirs() }
    private var isInitialized = false

    suspend fun initialize(onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {

        if (isInitialized) return@withContext true

        val modelFile = File(modelDir, MODEL_NAME)
        if (!modelFile.exists()) {
            downloadModel(modelFile, onProgress)
        }

        Log.i(TAG, "Initializing LLM at ${modelFile.absolutePath}")
        val ok = LlamaBridge.initNative(modelFile.absolutePath, N_CTX)
        isInitialized = ok

        if (!ok) Log.e(TAG, "Failed to initialize LLM")
        ok
    }

    fun infer(prompt: String, maxTokens: Int = 128): String {
        return if (!isInitialized) "[LLM not initialized]"
        else LlamaBridge.generateNative(prompt, maxTokens)
    }

    fun close() {
        if (isInitialized) {
            LlamaBridge.shutdownNative()
            isInitialized = false
        }
    }

    private suspend fun downloadModel(destinationFile: File, onProgress: (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Downloading model from $MODEL_URL...")
            val tempFile = File(modelDir, "$MODEL_NAME.part")

            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP ${connection.responseCode} while downloading model")
            }

            val totalSize = connection.contentLengthLong
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            onProgress(((downloaded * 100) / totalSize).toInt())
                        }
                    }
                }
            }

            if (destinationFile.exists()) destinationFile.delete()
            tempFile.renameTo(destinationFile)
            Log.i(TAG, "Model downloaded to ${destinationFile.absolutePath}")
        }
    }
}