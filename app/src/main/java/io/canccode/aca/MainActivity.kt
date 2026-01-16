// File: app/src/main/java/io/canccode/aca/MainActivity.kt
package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.commit
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "MainActivity"

        // Replace with a real .gguf model URL
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val MODEL_FILENAME = "model.gguf"
        private const val N_CTX = 512
    }

    lateinit var projectLoader: ProjectLoader
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var floatingMenuButton: android.widget.ImageView
    private lateinit var llmProgressBar: ProgressBar

    private var dX = 0f
    private var dY = 0f
    private var isDragging = false

    private var currentModeFragment: androidx.fragment.app.Fragment = EditorFragment()
    private val llmFragment = LLMFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        val fragmentContainer = findViewById<android.widget.FrameLayout>(R.id.fragment_container)
        floatingMenuButton = findViewById(R.id.floatingMenuButton)
        llmProgressBar = findViewById(R.id.llm_progress_bar)

        setupFloatingMenu()

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, currentModeFragment)
                add(R.id.llm_container, llmFragment)
            }
        }

        // Initialize LLM
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) { llmProgressBar.visibility = ProgressBar.VISIBLE }

                val modelFile = File(filesDir, MODEL_FILENAME)
                if (!modelFile.exists()) {
                    Log.i(TAG, "Downloading model...")
                    downloadModel(modelFile)
                } else {
                    Log.i(TAG, "Model exists at ${modelFile.absolutePath}")
                }

                Log.i(TAG, "Initializing LLM...")
                val ok = LlamaBridge.initNative(modelFile.absolutePath, N_CTX)
                Log.i(TAG, "LLM init result = $ok")

                if (ok) {
                    val output = LlamaBridge.generateNative("Hello LLM!", 64)
                    Log.i(TAG, "LLM test output: $output")
                } else {
                    Log.e(TAG, "Failed to initialize LLM")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "LLM failed to initialize", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing LLM", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "LLM error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { llmProgressBar.visibility = ProgressBar.GONE }
            }
        }
    }

    private fun setupFloatingMenu() {
        floatingMenuButton.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    isDragging = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX + dX).coerceIn(0f, drawerLayout.width - v.width.toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, drawerLayout.height - v.height.toFloat())
                    if (abs(v.x - newX) > 10 || abs(v.y - newY) > 10) isDragging = true
                    v.x = newX
                    v.y = newY
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!isDragging) drawerLayout.openDrawer(GravityCompat.START)
                }
            }
            true
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

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_files -> switchMode(FileBrowserFragment())
            R.id.nav_editor -> switchMode(EditorFragment())
            R.id.nav_llm -> Toast.makeText(this, "LLM is always available", Toast.LENGTH_SHORT).show()
            R.id.nav_load_project -> pickProjectFolder()
            R.id.nav_reload_model -> reloadModel()
            R.id.nav_clear_console -> Toast.makeText(this, "Clearing console...", Toast.LENGTH_SHORT).show()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun switchMode(fragment: androidx.fragment.app.Fragment) {
        currentModeFragment = fragment
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
        }
    }

    private fun pickProjectFolder() {
        // Implement your folder picker logic if needed
    }

    private fun reloadModel() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) { llmProgressBar.visibility = ProgressBar.VISIBLE }
                LlamaBridge.shutdownNative()

                val modelFile = File(filesDir, MODEL_FILENAME)
                if (!modelFile.exists()) downloadModel(modelFile)

                val ok = LlamaBridge.initNative(modelFile.absolutePath, N_CTX)
                Log.i(TAG, "Reloaded model: $ok")
                if (!ok) withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to reload model", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to reload model", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { llmProgressBar.visibility = ProgressBar.GONE }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Shutting down LLM...")
        LlamaBridge.shutdownNative()
    }
}