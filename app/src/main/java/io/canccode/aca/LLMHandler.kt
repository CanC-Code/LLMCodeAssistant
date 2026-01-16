// File: app/src/main/java/com/llmassistant/llm/LLMHandler.kt
package com.llmassistant.llm

import android.content.Context
import android.util.Log
import io.canccode.aca.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val MODEL_FILENAME = "tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val N_CTX = 512
    }

    private val modelFile: File by lazy {
        File(context.filesDir, MODEL_FILENAME)
    }

    /**
     * Initialize the model: downloads if missing, then calls LlamaBridge.initNative
     * Returns true if initialization succeeded
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.exists()) {
                Log.i(TAG, "Downloading LLM model...")
                downloadModel(modelFile)
            } else {
                Log.i(TAG, "LLM model already exists: ${modelFile.absolutePath}")
            }

            Log.i(TAG, "Initializing LLM via LlamaBridge...")
            val ok = LlamaBridge.initNative(modelFile.absolutePath, N_CTX)
            Log.i(TAG, "LLM init result = $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            false
        }
    }

    /**
     * Generates text from a prompt
     */
    suspend fun generate(prompt: String, maxTokens: Int = 64): String = withContext(Dispatchers.IO) {
        try {
            LlamaBridge.generateNative(prompt, maxTokens)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating text", e)
            ""
        }
    }

    /**
     * Shutdown the LLM
     */
    fun shutdown() {
        try {
            Log.i(TAG, "Shutting down LLM via LlamaBridge...")
            LlamaBridge.shutdownNative()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down LLM", e)
        }
    }

    /**
     * Downloads GGUF model from MODEL_URL into destination file
     */
    private suspend fun downloadModel(destinationFile: File) = withContext(Dispatchers.IO) {
        val url = URL(MODEL_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("HTTP ${connection.responseCode} downloading model")
        }

        val totalSize = connection.contentLengthLong
        var downloaded = 0L

        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read

                    if (totalSize > 0) {
                        val progress = ((downloaded * 100) / totalSize).toInt()
                        Log.i(TAG, "Download progress: $progress%")
                    }
                }
            }
        }
        Log.i(TAG, "Model downloaded to ${destinationFile.absolutePath}")
    }
}