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

        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"

        private const val MODEL_FILENAME = "model.gguf"
        private const val CONTEXT_SIZE = 2048
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "App started")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelFile = File(filesDir, MODEL_FILENAME)

                if (!modelFile.exists()) {
                    Log.i(TAG, "Downloading model...")
                    downloadModel(modelFile)
                }

                Log.i(TAG, "Initializing LLM...")
                val ok = LlamaBridge.initNative(
                    modelFile.absolutePath,
                    CONTEXT_SIZE
                )

                Log.i(TAG, "LLM init result = $ok")

            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
            }
        }
    }

    private suspend fun downloadModel(destinationFile: File) {
        withContext(Dispatchers.IO) {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
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
        LlamaBridge.shutdownNative()
    }
}