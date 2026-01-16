// File: app/src/main/java/io/canccode/aca/MainActivity.kt
package io.canccode.aca

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        // Replace this with a real direct .gguf file link
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val MODEL_FILENAME = "model.gguf"
        private const val N_CTX = 512  // context size for llama.cpp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "App started")

        // Launch LLM initialization
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelFile = File(filesDir, MODEL_FILENAME)

                if (!modelFile.exists()) {
                    Log.i(TAG, "Downloading model...")
                    downloadModel(modelFile)
                } else {
                    Log.i(TAG, "Model already exists: ${modelFile.absolutePath}")
                }

                Log.i(TAG, "Initializing LLM...")
                val ok = LlamaBridge.initNative(modelFile.absolutePath, N_CTX)
                Log.i(TAG, "LLM init result = $ok")

                if (ok) {
                    // Test prompt
                    val output = LlamaBridge.generateNative("Hello LLM!", 64)
                    Log.i(TAG, "LLM test output: $output")
                } else {
                    Log.e(TAG, "Failed to initialize LLM")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing LLM", e)
            }
        }
    }

    private suspend fun downloadModel(destinationFile: File) {
        withContext(Dispatchers.IO) {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Model downloaded to ${destinationFile.absolutePath}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Shutting down LLM...")
        LlamaBridge.shutdownNative()
    }
}