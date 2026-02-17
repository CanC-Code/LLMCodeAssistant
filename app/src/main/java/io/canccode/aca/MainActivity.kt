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

    private lateinit var llmHandler: LLMHandler
    private val projectLoader = ProjectLoader(this)
    private var llmInitialized = false

    override fun getProjectLoader(): ProjectLoader = projectLoader

    // SAF Picker for the Project Directory
    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { handleProjectLoad(it) }
    }

    // SAF Picker for the GGUF Model File
    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleModelImport(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        llmHandler = LLMHandler(this)

        initDrawer()
        initFloatingButton()
        initGlobalLLMInputs()
        setupBackPressed()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }
    }

    private fun handleModelImport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.Main) {
            updateLLMUIState(false, "Importing Model...")
            
            val cachedPath = withContext(Dispatchers.IO) {
                llmHandler.prepareModelFromUri(uri)
            }

            if (cachedPath != null) {
                initLLM(cachedPath)
            } else {
                Toast.makeText(this@MainActivity, "Failed to import model", Toast.LENGTH_LONG).show()
                updateLLMUIState(false, "Import Failed")
            }
        }
    }

    private fun initLLM(path: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            // Free previous model memory before loading new one
            LlamaBridge.shutdownNative()
            
            // Qwen2.5-Coder-3B works best with 2048-4096 context on mobile
            val success = LlamaBridge.init(path, 2048)

            withContext(Dispatchers.Main) {
                llmInitialized = success
                if (success) {
                    updateLLMUIState(true, "")
                    Toast.makeText(this@MainActivity, "Model Loaded Successfully", Toast.LENGTH_SHORT).show()
                } else {
                    updateLLMUIState(false, "JNI Load Failed")
                }
            }
        }
    }

    /**
     * SettingsFragment.OnSettingsChangedListener implementations
     */
    override fun onModelSelectionChanged(modelFile: File?) {
        // Handled via SAF picker in this refactored version
    }

    override fun onThemeChanged(isDarkMode: Boolean) {
        // Implementation for theme switching
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
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            LlamaBridge.generateNative(prompt, 512, object : LlamaBridge.GenerateCallback {
                override fun onToken(piece: String) {
                    // Log or stream to a specialized Console Fragment here
                }

                override fun onComplete(fullResponse: String) {
                    runOnUiThread {
                        // For global input, we show a snippet. 
                        // In LLMFragment, we would append to a RecyclerView.
                        Toast.makeText(this@MainActivity, "Response received", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "LLM Error: $error", Toast.LENGTH_SHORT).show()
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
        // Persist access so the user doesn't have to pick the folder every time
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
                Log.e(TAG, "Project Load Error", e)
            }
        }
    }

    private fun updateLLMUIState(isReady: Boolean, status: String) {
        llmInitialized = isReady
        llmSendGlobal.isEnabled = isReady
        llmInputGlobal.isEnabled = true 
        
        if (isReady) {
            llmInputGlobal.hint = "Ask the Assistant..."
        } else {
            llmInputGlobal.hint = if (status.isEmpty()) "Select Model via Drawer" else status
        }
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
        LlamaBridge.shutdownNative()
    }
}
