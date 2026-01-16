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

        // REPLACE THIS WITH A REAL DIRECT .gguf FILE
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"

        private const val MODEL_FILENAME = "model.gguf"
    }

    // JNI bindings
    external fun initLlama(modelPath: String): Boolean
    external fun freeLlama()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "App started")

        CoroutineScope(Dispatchers.IO).launch {
            val modelFile = File(filesDir, MODEL_FILENAME)

            if (!modelFile.exists()) {
                Log.i(TAG, "Downloading model...")
                downloadModel(modelFile)
            }

            Log.i(TAG, "Initializing LLM...")
            val ok = initLlama(modelFile.absolutePath)
            Log.i(TAG, "LLM init result = $ok")
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
        freeLlama()
    }

    init {
        System.loadLibrary("llama_jni")
    }
}