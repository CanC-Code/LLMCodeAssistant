// File: app/src/main/java/io/canccode/aca/MainActivity.kt
package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // ⚠️ MUST be a real, direct-download .gguf file
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.q4_0.gguf"

        private const val MODEL_FILENAME = "model.gguf"
    }

    private lateinit var statusText: TextView
    private lateinit var initButton: Button

    // JNI
    external fun initLlama(modelPath: String): Boolean
    external fun freeLlama()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        initButton = findViewById(R.id.initButton)

        initButton.setOnClickListener {
            checkAndInitModel()
        }
    }

    private fun checkAndInitModel() {
        val modelFile = File(filesDir, MODEL_FILENAME)

        if (modelFile.exists()) {
            statusText.text = "Model found. Initializing…"
            initLlm(modelFile)
            return
        }

        statusText.text = "Downloading model…"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                downloadModel(modelFile)

                withContext(Dispatchers.Main) {
                    statusText.text = "Download complete. Initializing…"
                    initLlm(modelFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                withContext(Dispatchers.Main) {
                    statusText.text = "Download failed:\n${e.message}"
                }
            }
        }
    }

    private fun initLlm(modelFile: File) {
        try {
            val success = initLlama(modelFile.absolutePath)
            statusText.text =
                if (success) "LLM initialized successfully"
                else "LLM failed to initialize"
        } catch (e: Throwable) {
            Log.e(TAG, "LLM init crashed", e)
            statusText.text = "Native crash:\n${e.message}"
        }
    }

    private fun downloadModel(destinationFile: File) {
        val url = URL(MODEL_URL)
        val connection = url.openConnection() as HttpURLConnection

        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("HTTP ${connection.responseCode}")
        }

        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
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