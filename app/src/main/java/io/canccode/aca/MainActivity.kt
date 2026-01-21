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
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import io.canccode.aca.SettingsFragment.Companion.KEY_MODEL_PATH
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

    // SAF directory picker for projects
    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleProjectLoad(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "Setting content view")
            setContentView(R.layout.activity_main)

            prefs = getSharedPreferences("model_prefs", MODE_PRIVATE)
            loadLastModelPath()

            initDrawer()
            initFloatingButton()
            initGlobalLLM()

            if (savedInstanceState == null) {
                loadFragment(LLMFragment())
            }

            // Auto-init LLM if model path exists
            tryAutoInitLLM()

            Log.d(TAG, "MainActivity onCreate complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadLastModelPath() {
        currentModelPath = prefs.getString(KEY_MODEL_PATH, null)
        Log.i(TAG, "Loaded last model path: $currentModelPath")
    }

    fun onModelSelectionChanged() {
        Log.d(TAG, "Model selection changed, reloading...")
        loadLastModelPath()
        tryAutoInitLLM()
    }

    private fun tryAutoInitLLM() {
        val path = currentModelPath
        
        if (path.isNullOrEmpty()) {
            Log.i(TAG, "No model path configured")
            runOnUiThread {
                Toast.makeText(
                    this,
                    "No model selected. Go to Settings to choose a model.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        Log.i(TAG, "Attempting to initialize model: $path")
        
        runOnUiThread {
            Toast.makeText(this, "Initializing model...", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var errorMsg: String? = null
            
            try {
                val file = File(path)
                
                if (!file.exists()) {
                    errorMsg = "Model file not found:\n${file.name}"
                    Log.e(TAG, "Model file does not exist: $path")
                } else if (!file.canRead()) {
                    errorMsg = "Cannot read model file:\n${file.name}"
                    Log.e(TAG, "Cannot read model file: $path")
                } else {
                    Log.i(TAG, "Loading model from: ${file.absolutePath}")
                    Log.i(TAG, "Model file size: ${file.length() / (1024 * 1024)}MB")
                    
                    // Shutdown any existing model first
                    try {
                        LlamaBridge.shutdownNative()
                        Log.d(TAG, "Shut down previous model instance")
                    } catch (e: Exception) {
                        Log.d(TAG, "No previous model to shutdown")
                    }
                    
                    success = LlamaBridge.initNative(file.absolutePath, 2048)
                    
                    if (success) {
                        Log.i(TAG, "Model initialized successfully!")
                    } else {
                        errorMsg = "Model initialization failed (returned false)"
                        Log.e(TAG, errorMsg)
                    }
                }
            } catch (e: Throwable) {
                errorMsg = "Model initialization error: ${e.message}"
                Log.e(TAG, "Model initialization failed", e)
            }

            withContext(Dispatchers.Main) {
                setLLMInitialized(success)
                
                if (success) {
                    Toast.makeText(
                        this@MainActivity,
                        "✓ Model loaded successfully!",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Hide progress bar in SettingsFragment if visible
                    val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
                    settingsFragment?.onModelInitComplete(true)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "✗ Failed to load model\n${errorMsg ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
                    settingsFragment?.onModelInitComplete(false)
                }
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

    private fun initGlobalLLM() {
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
            Toast.makeText(this, "LLM not initialized. Load a model in Settings first.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = LlamaBridge.generateNative(prompt, 256)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, response.take(200), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_load_project -> directoryPicker.launch(null)
            R.id.nav_file_browser -> loadFragment(FileBrowserFragment.newInstance(projectLoader))
            R.id.nav_llm -> loadFragment(LLMFragment())
            R.id.nav_settings -> loadFragment(SettingsFragment())
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
                    loadFragment(FileBrowserFragment.newInstance(projectLoader))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun setLLMInitialized(initialized: Boolean) {
        llmInitialized = initialized
        runOnUiThread {
            llmSendGlobal.isEnabled = initialized
            llmInputGlobal.isEnabled = initialized
            
            if (initialized) {
                llmInputGlobal.hint = "Ask LLM..."
            } else {
                llmInputGlobal.hint = "No model loaded - go to Settings"
            }
        }
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
        return if (toggle.onOptionsItemSelected(item)) true else super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (llmInitialized) {
            LlamaBridge.shutdownNative()
        }
    }
}