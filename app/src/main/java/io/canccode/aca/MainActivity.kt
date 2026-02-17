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
import androidx.activity.viewModels
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

    // Shared ViewModel — the single source of truth for model state
    private val appViewModel: AppViewModel by viewModels()

    private val projectLoader = ProjectLoader(this)

    override fun getProjectLoader(): ProjectLoader = projectLoader

    // SAF Picker for the Project Directory
    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { handleProjectLoad(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initDrawer()
        initFloatingButton()
        initGlobalLLMInputs()
        setupBackPressed()
        observeViewModel()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }
    }

    /**
     * Observe the shared ViewModel to keep the global send bar in sync
     * with whatever SettingsFragment does.
     */
    private fun observeViewModel() {
        appViewModel.isModelLoaded.observe(this) { isLoaded ->
            llmSendGlobal.isEnabled = isLoaded
            llmInputGlobal.hint = if (isLoaded) "Ask the Assistant..." else "Select a model first (Settings & Model)"
        }
    }

    // -------------------------------------------------------------------------
    // SettingsFragment.OnSettingsChangedListener
    // -------------------------------------------------------------------------

    /**
     * Called by SettingsFragment after model init succeeds.
     * The heavy lifting (LlamaBridge.init + appViewModel.setModelLoaded) is already
     * done inside SettingsFragment. This callback is kept for any additional
     * MainActivity-level UI updates that may be needed in the future.
     */
    override fun onModelSelectionChanged(modelFile: File?) {
        if (modelFile != null) {
            Log.i(TAG, "Model selection confirmed: ${modelFile.name}")
            // ViewModel is already updated by SettingsFragment; nothing extra needed here.
        } else {
            Log.i(TAG, "Model cleared")
            // ViewModel is already cleared by SettingsFragment.
        }
    }

    override fun onThemeChanged(isDarkMode: Boolean) {
        // Implementation for theme switching if needed
    }

    // -------------------------------------------------------------------------
    // Global LLM input bar (bottom of activity_main layout)
    // -------------------------------------------------------------------------

    private fun initGlobalLLMInputs() {
        llmInputGlobal = findViewById(R.id.llm_input_global)
        llmSendGlobal = findViewById(R.id.llm_send_global)

        // Start disabled — enabled by ViewModel observer when model is ready
        llmSendGlobal.isEnabled = false
        llmInputGlobal.hint = "Select a model first (Settings & Model)"

        llmSendGlobal.setOnClickListener {
            val prompt = llmInputGlobal.text.toString().trim()
            if (prompt.isNotEmpty()) {
                sendToLLMGlobal(prompt)
                llmInputGlobal.text.clear()
            }
        }
    }

    private fun sendToLLMGlobal(prompt: String) {
        if (appViewModel.isModelLoaded.value != true) {
            Toast.makeText(this, "Select a model first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            LlamaBridge.generateNative(prompt, 512, object : LlamaBridge.GenerateCallback {
                override fun onToken(piece: String) { /* streamed to LLMFragment */ }

                override fun onComplete(fullResponse: String) {
                    runOnUiThread {
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

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private fun initDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val fragment = when (item.itemId) {
            R.id.nav_load_project -> { directoryPicker.launch(null); null }
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

    // -------------------------------------------------------------------------
    // Floating button
    // -------------------------------------------------------------------------

    private fun initFloatingButton() {
        floatingMenuButton = findViewById(R.id.floatingMenuButton)
        enableDragAndClick(floatingMenuButton)
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
                        if (drawerLayout.isDrawerOpen(GravityCompat.START))
                            drawerLayout.closeDrawer(GravityCompat.START)
                        else
                            drawerLayout.openDrawer(GravityCompat.START)
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

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        LlamaBridge.shutdown()
    }
}
