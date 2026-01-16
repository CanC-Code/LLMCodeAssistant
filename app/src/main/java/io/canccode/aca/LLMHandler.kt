package io.canccode.aca

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LLMHandler(private val context: Context) {

    private val TAG = "LLMHandler"
    private val MODEL_NAME = "ggml-alpaca-7b-q4.bin"  // change to your desired GGUF
    private val MODEL_URL = "https://huggingface.co/ccvo/alpaca-7b-q4/resolve/main/$MODEL_NAME"

    private val modelDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    private val modelFile: File by lazy {
        File(modelDir, MODEL_NAME)
    }

    /**
     * Ensures the model file exists locally. Downloads if missing.
     */
    fun ensureModelReady() {
        if (!modelFile.exists()) {
            Log.i(TAG, "Model file not found, downloading...")
            downloadModel()
        } else {
            Log.i(TAG, "Model file exists: ${modelFile.absolutePath}")
        }

        // Initialize native LLM bridge
        LlamaBridge.initNative(modelFile.absolutePath, 512) // 512-context example
        Log.i(TAG, "Model ready at: ${modelFile.absolutePath}")
    }

    private fun downloadModel() {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(MODEL_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            connection.inputStream.use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Model downloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model", e)
            throw e
        } finally {
            connection?.disconnect()
        }
    }
}