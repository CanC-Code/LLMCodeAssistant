// File: app/src/main/java/io/canccode/aca/MainActivity.kt
package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val MODEL_FILENAME = "tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val N_CTX = 512 // context size
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var floatingMenuButton: ImageView
    private lateinit var llmProgressBar: ProgressBar

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        floatingMenuButton = findViewById(R.id.floatingMenuButton)
        llmProgressBar = findViewById(R.id.llm_progress_bar)

        setupFloatingMenu()

        // Start LLM initialization on app launch
        mainScope.launch {
            initializeLLM()
        }
    }

    private suspend fun initializeLLM() = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(filesDir, MODEL_FILENAME)

            // Show progress bar on UI
            withContext(Dispatchers.Main) {
                llmProgressBar.visibility = ProgressBar.VISIBLE
                llmProgressBar.progress = 0
            }

            // Download model if it doesn't exist
            if (!modelFile.exists()) {
                Log.i(TAG, "Downloading model...")
                downloadModel(modelFile)
            } else {
                Log.i(TAG, "Model already exists: ${modelFile.absolutePath}")
            }

            // Initialize native LLM
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
        } finally {
            withContext(Dispatchers.Main) {
                llmProgressBar.visibility = ProgressBar.GONE
            }
        }
    }

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

                    // Update progress bar
                    withContext(Dispatchers.Main) {
                        if (totalSize > 0) {
                            val progress = ((downloaded * 100) / totalSize).toInt()
                            llmProgressBar.progress = progress
                        }
                    }
                }
            }
        }

        Log.i(TAG, "Model downloaded to ${destinationFile.absolutePath}")
    }

    private fun setupFloatingMenu() {
        floatingMenuButton.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.tag = Pair(v.x - event.rawX, v.y - event.rawY)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val (dX, dY) = v.tag as Pair<Float, Float>
                    val newX = (event.rawX + dX).coerceIn(0f, drawerLayout.width - v.width.toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, drawerLayout.height - v.height.toFloat())
                    v.x = newX
                    v.y = newY
                }
            }
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        Log.i(TAG, "Shutting down LLM...")
        LlamaBridge.shutdownNative()
    }

    init {
        System.loadLibrary("llama_jni")
    }
}