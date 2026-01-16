// File: app/src/main/java/io/canccode/aca/MainActivity.kt
package io.canccode.aca

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val MODEL_URL = "https://example.com/models/your_model.gguf" // replace with your model URL
        private const val MODEL_FILENAME = "your_model.gguf"
    }

    private lateinit var statusText: TextView
    private lateinit var initButton: Button

    // JNI function from llama_jni.cpp
    external fun initLlama(modelPath: String): Boolean
    external fun freeLlama()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(TAG, "Storage/Internet permission granted")
            checkAndInitModel()
        } else {
            Log.e(TAG, "Permission denied")
            statusText.text = "Permission denied. Cannot load model."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        initButton = findViewById(R.id.initButton)

        initButton.setOnClickListener {
            checkPermissionsAndInit()
        }
    }

    private fun checkPermissionsAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.INTERNET)
        } else {
            checkAndInitModel()
        }
    }

    private fun checkAndInitModel() {
        val modelFile = File(filesDir, MODEL_FILENAME)

        if (!modelFile.exists()) {
            statusText.text = "Downloading model..."
            CoroutineScope(Dispatchers.IO).launch {
                downloadModel(modelFile)
                withContext(Dispatchers.Main) {
                    statusText.text = "Model downloaded. Initializing..."
                    initLlm(modelFile)
                }
            }
        } else {
            statusText.text = "Model exists. Initializing..."
            initLlm(modelFile)
        }
    }

    private fun initLlm(modelFile: File) {
        try {
            val success = initLlama(modelFile.absolutePath)
            statusText.text = if (success) "LLM initialized successfully!" else "LLM failed to initialize."
        } catch (e: Exception) {
            Log.e(TAG, "LLM initialization error", e)
            statusText.text = "LLM initialization error: ${e.message}"
        }
    }

    private suspend fun downloadModel(destinationFile: File) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(MODEL_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                connection.inputStream.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                throw e
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        freeLlama() // release resources
    }

    init {
        System.loadLibrary("llama_jni")
    }
}