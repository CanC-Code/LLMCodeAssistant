package io.canccode.aca

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "MainActivity"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var floatingMenuButton: ImageView
    private lateinit var llmInputGlobal: EditText
    private lateinit var llmSendGlobal: Button

    private val projectLoader = ProjectLoader(this)
    private var llmInitialized = false

    private var currentModelPath: String? = null
    private lateinit var prefs: SharedPreferences

    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleProjectLoad(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("model_prefs", MODE_PRIVATE)
        loadLastModelPath()

        initDrawer()
        initFloatingButton()
        initGlobalLLMInputs()
        tryAutoInitLLM()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }
    }

    private fun loadLastModelPath() {
        currentModelPath = prefs.getString("selected_model_path", null)
            ?: prefs.getString("selected_model_uri", null)
        Log.i(TAG, "Loaded last model path: $currentModelPath")
    }

    fun onModelSelectionChanged() {
        loadLastModelPath()
        tryAutoInitLLM()
    }

    private fun tryAutoInitLLM() {
        val path = currentModelPath ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                success = if (path.startsWith("content://")) {
                    Log.w(TAG, "SAF content:// URI detected - not supported by llama.cpp yet.")
                    false
                } else {
                    val file = File(path)
                    if (file.exists() && file.canRead()) {
                        LlamaBridge.initNative(file.absolutePath, 2048)
                    } else {
                        Log.w(TAG, "Model file not found or unreadable: $path")
                        false
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Model initialization failed", e)
            }

            withContext(Dispatchers.Main) {
                llmInitialized = success
                llmSendGlobal.isEnabled = success
                llmInputGlobal.isEnabled = success
                Toast.makeText(
                    this@MainActivity,
                    if (success) "Model loaded successfully" else "Failed to load model",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun initDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initFloatingButton() {
        floatingMenuButton = findViewById(R.id.floatingMenuButton)
        enableDragAndClick(floatingMenuButton)
    }

    private fun initGlobalLLMInputs() {
        llmInputGlobal = findViewById(R.id.llm_input_global)
        llmSendGlobal = findViewById(R.id.llm_send_global)

        llmSendGlobal.setOnClickListener {
            val prompt = llmInputGlobal.text.toString().trim()
            if (prompt.isNotEmpty()) {
                sendToLLM(prompt)
                llmInputGlobal.text.clear()
            }
        }
    }

    private fun sendToLLM(prompt: String) {
        if (!llmInitialized) {
            Toast.makeText(this, "LLM not initialized yet", Toast.LENGTH_SHORT).show()
            return
        }

        // Show immediate feedback
        Toast.makeText(this, "Generating...", Toast.LENGTH_SHORT).show()

        LlamaBridge.generateNative(prompt, 512, object : LlamaBridge.GenerateCallback {
            override fun onToken(piece: String) {
                // Optional: could append to a TextView here if you want live streaming
                // For simplicity we're collecting and showing at end
            }

            override fun onComplete(fullResponse: String) {
                val displayText = if (fullResponse.length > 200) {
                    fullResponse.substring(0, 200) + "..."
                } else {
                    fullResponse
                }

                Toast.makeText(
                    this@MainActivity,
                    displayText,
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onError(error: String) {
                Toast.makeText(
                    this@MainActivity,
                    "Generation error: $error",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_load_project -> directoryPicker.launch(null)
            R.id.nav_file_browser -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
                .commit()
            R.id.nav_llm -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
            R.id.nav_settings -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
        }

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun handleProjectLoad(treeUri: Uri) {
        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        Toast.makeText(this, "Loading project...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                projectLoader.loadProject(treeUri)
                runOnUiThread {
                    Toast.makeText(this, "Project loaded", Toast.LENGTH_SHORT).show()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
                        .commit()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    fun getProjectLoader(): ProjectLoader = projectLoader

    private fun enableDragAndClick(view: View) {
        var dX = 0f
        var dY = 0f
        var downX = 0f
        var downY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    downX = event.rawX
                    downY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.rawX - downX)
                    val deltaY = abs(event.rawY - downY)

                    if (deltaX > 10 || deltaY > 10) {
                        isDragging = true
                        v.animate()
                            .x(event.rawX + dX)
                            .y(event.rawY + dY)
                            .setDuration(0)
                            .start()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                            drawerLayout.closeDrawer(GravityCompat.START)
                        } else {
                            drawerLayout.openDrawer(GravityCompat.START)
                        }
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (toggle.onOptionsItemSelected(item)) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (llmInitialized) {
            LlamaBridge.shutdownNative()
        }
    }
}