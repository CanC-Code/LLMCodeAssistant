package io.canccode.aca

import android.content.Intent
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

interface ProjectProvider {
    fun getProjectLoader(): ProjectLoader
}

class MainActivity : AppCompatActivity(), 
    NavigationView.OnNavigationItemSelectedListener, 
    SettingsFragment.OnSettingsChangedListener,
    ProjectProvider {

    private val TAG = "MainActivity"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var floatingMenuButton: ImageView
    private lateinit var llmInputGlobal: EditText
    private lateinit var llmSendGlobal: Button

    private lateinit var modelManager: ModelManager
    private val projectLoader = ProjectLoader(this)
    private var llmInitialized = false

    override fun getProjectLoader(): ProjectLoader = projectLoader

    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { handleProjectLoad(it) }
    }

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleModelImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modelManager = ModelManager(this)

        initDrawer()
        initFloatingButton()
        initGlobalLLMInputs()
        setupBackPressed()

        tryAutoInitLLM()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }
    }

    override fun onModelSelectionChanged(modelFile: File?) {
        if (modelFile != null) {
            tryAutoInitLLM()
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                LlamaBridge.shutdownNative()
                withContext(Dispatchers.Main) {
                    updateLLMUIState(false, "Model unloaded")
                }
            }
        }
    }

    override fun onThemeChanged(isDarkMode: Boolean) {
        // Implement Theme change logic if needed
    }

    private fun handleModelImport(uri: Uri) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Importing model...", Toast.LENGTH_SHORT).show()
            val success = modelManager.importModelFromUri(uri) { /* Progress */ }
            if (success) tryAutoInitLLM()
        }
    }

    private fun tryAutoInitLLM() {
        val path = modelManager.getModelPath()
        if (path.isNullOrBlank()) {
            updateLLMUIState(false, "No model selected")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val file = File(path)
                if (file.exists()) {
                    LlamaBridge.shutdownNative()
                    // 4096 context is standard for Qwen/Llama coding models
                    success = LlamaBridge.initNative(file.absolutePath, 4096)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Init Error", e)
            }

            withContext(Dispatchers.Main) {
                llmInitialized = success
                updateLLMUIState(success, if (success) "" else "Init failed")
                
                val currentFrag = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFrag is SettingsFragment) {
                    currentFrag.onModelInitComplete(success)
                }
            }
        }
    }

    private fun initDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)
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
            Toast.makeText(this, "Model not ready", Toast.LENGTH_SHORT).show()
            return
        }

        // Run generation in background to prevent UI freeze
        lifecycleScope.launch(Dispatchers.Default) {
            LlamaBridge.generateNative(prompt, 512, object : LlamaBridge.GenerateCallback {
                override fun onToken(piece: String) {
                    // Streaming global output isn't implemented in the UI yet, but we could log it
                }

                override fun onComplete(fullResponse: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "AI: ${fullResponse.take(100)}...", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val fragment = when (item.itemId) {
            R.id.nav_load_project -> { directoryPicker.launch(null); null }
            R.id.nav_load_model -> { modelPicker.launch(arrayOf("*/*")); null }
            R.id.nav_file_browser -> FileBrowserFragment.newInstance(projectLoader)
            R.id.nav_llm -> LLMFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> null
        }

        fragment?.let {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, it)
                .addToBackStack(null)
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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                projectLoader.loadProject(treeUri)
                withContext(Dispatchers.Main) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
                        .commit()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Project Load Error", e)
            }
        }
    }

    private fun updateLLMUIState(isReady: Boolean, reason: String) {
        llmInitialized = isReady
        llmSendGlobal.isEnabled = isReady
        llmInputGlobal.isEnabled = isReady
        val modelName = if (isReady) modelManager.getModelDisplayName() else "None"
        llmInputGlobal.hint = if (isReady) "Ask $modelName..." else "Model Not Ready: $reason"
    }

    private fun enableDragAndClick(view: View) {
        var dX = 0f; var dY = 0f; var downX = 0f; var downY = 0f; var isDragging = false
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX; dY = v.y - event.rawY
                    downX = event.rawX; downY = event.rawY
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) > 10 || abs(event.rawY - downY) > 10) {
                        isDragging = true
                        v.x = event.rawX + dX; v.y = event.rawY + dY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START)
                        else drawerLayout.openDrawer(GravityCompat.START)
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure native resources are freed
        LlamaBridge.shutdownNative()
    }
}
