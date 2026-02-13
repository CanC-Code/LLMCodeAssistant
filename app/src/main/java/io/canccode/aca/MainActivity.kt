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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

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

    // SAF Directory Picker for Projects
    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleProjectLoad(it) }
    }

    // SAF File Picker for GGUF Models
    private val modelPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleModelImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modelManager = ModelManager(this)

        initDrawer()
        initFloatingButton()
        initGlobalLLMInputs()
        
        // Attempt to load the last used model automatically
        tryAutoInitLLM()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }
    }

    /**
     * Handles the import of a user-selected GGUF file via SAF.
     */
    private fun handleModelImport(uri: Uri) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Importing model to internal storage...", Toast.LENGTH_SHORT).show()
            
            val success = modelManager.importModelFromUri(uri) { progress ->
                Log.d(TAG, "Import Progress: $progress%")
            }

            if (success) {
                tryAutoInitLLM() // Re-initialize with the new local file
            } else {
                Toast.makeText(this@MainActivity, "Failed to import model.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun tryAutoInitLLM() {
        val path = modelManager.getModelPath()
        
        if (path.isNullOrBlank()) {
            Log.w(TAG, "No model path found. User needs to select a model.")
            updateLLMUIState(false, "No model selected")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var failureReason = ""

            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    Log.i(TAG, "Initializing Qwen/GGUF: ${file.name}")
                    // Context set to 4096 for coding tasks
                    success = LlamaBridge.initNative(file.absolutePath, 4096)
                    if (!success) failureReason = "LlamaBridge init failed"
                } else {
                    failureReason = "Model file missing or unreadable"
                }
            } catch (e: Exception) {
                failureReason = e.message ?: "Unknown error"
                Log.e(TAG, "Init Crash", e)
            }

            withContext(Dispatchers.Main) {
                llmInitialized = success
                updateLLMUIState(success, failureReason)
                
                if (success) {
                    Toast.makeText(this@MainActivity, "🤖 ${modelManager.getModelDisplayName()} Ready", Toast.LENGTH_SHORT).show()
                } else if (failureReason.isNotEmpty()) {
                    Toast.makeText(this@MainActivity, "Load Error: $failureReason", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Select a model first", Toast.LENGTH_SHORT).show()
            modelPicker.launch(arrayOf("*/*"))
            return
        }

        Toast.makeText(this, "Generating...", Toast.LENGTH_SHORT).show()

        LlamaBridge.generateNative(prompt, 512, object : LlamaBridge.GenerateCallback {
            override fun onToken(piece: String) { /* Optional: UI Stream */ }

            override fun onComplete(fullResponse: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Response received", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_load_project -> directoryPicker.launch(null)
            R.id.nav_load_model -> modelPicker.launch(arrayOf("*/*")) // Use new SAF Model selection
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
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                projectLoader.loadProject(treeUri)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Project Loaded", Toast.LENGTH_SHORT).show()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
                        .commit()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateLLMUIState(isReady: Boolean, reason: String) {
        llmInitialized = isReady
        llmSendGlobal.isEnabled = isReady
        llmInputGlobal.isEnabled = isReady
        llmInputGlobal.hint = if (isReady) "Message ${modelManager.getModelDisplayName()}..." else "No Model Loaded"
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

    override fun onDestroy() {
        super.onDestroy()
        LlamaBridge.shutdownNative()
    }
}
