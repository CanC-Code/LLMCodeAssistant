package io.canccode.aca

import android.content.Context
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

    private val appViewModel: AppViewModel by viewModels()
    private val projectLoader = ProjectLoader(this)

    override fun getProjectLoader(): ProjectLoader = projectLoader

    // SAF picker for the project directory
    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { handleProjectLoad(it) } }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initDrawer()
        initFloatingButton()
        initGlobalLLMInputs()
        setupBackPressed()
        observeViewModel()

        // Auto-reload the last-used model so the user never has to re-pick it
        autoReloadModel()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }
    }

    /**
     * On every cold start, re-initialise the native context from the persisted
     * model file in filesDir.  The file itself never leaves app-private storage,
     * so it always exists until the user explicitly clears it.
     */
    private fun autoReloadModel() {
        val prefs      = getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
        val savedPath  = prefs.getString(SettingsFragment.KEY_MODEL_PATH, null) ?: return
        val file       = File(savedPath)

        if (!file.exists()) {
            Log.w(TAG, "Saved model missing at $savedPath — clearing pref")
            prefs.edit().remove(SettingsFragment.KEY_MODEL_PATH).apply()
            return
        }

        Log.i(TAG, "Auto-reloading model: ${file.name}")
        llmInputGlobal.hint = "Loading saved model…"

        lifecycleScope.launch(Dispatchers.IO) {
            LlamaBridge.shutdown()
            val success = LlamaBridge.init(savedPath, 4096)
            withContext(Dispatchers.Main) {
                if (success) {
                    appViewModel.setModelLoaded(savedPath)
                    Toast.makeText(
                        this@MainActivity,
                        "Model restored: ${file.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.e(TAG, "Auto-reload failed for $savedPath")
                    prefs.edit().remove(SettingsFragment.KEY_MODEL_PATH).apply()
                    Toast.makeText(
                        this@MainActivity,
                        "Saved model could not be loaded — please re-select",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Do NOT shut down the native layer in onDestroy.
    // The model file lives in filesDir and the next cold start reloads it.
    // The OS reclaims the process memory on its own.

    // ─────────────────────────────────────────────────────────────────────────
    // ViewModel observation
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        appViewModel.isModelLoaded.observe(this) { isLoaded ->
            llmSendGlobal.isEnabled = isLoaded
            llmInputGlobal.hint = if (isLoaded) "Ask the Assistant…"
                                  else           "Select a model (Settings & Model)"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SettingsFragment callbacks
    // ─────────────────────────────────────────────────────────────────────────

    override fun onModelSelectionChanged(modelFile: File?) {
        Log.i(TAG, if (modelFile != null) "Model ready: ${modelFile.name}" else "Model cleared")
    }

    override fun onThemeChanged(isDarkMode: Boolean) { /* future */ }

    // ─────────────────────────────────────────────────────────────────────────
    // Global LLM input bar
    // ─────────────────────────────────────────────────────────────────────────

    private fun initGlobalLLMInputs() {
        llmInputGlobal = findViewById(R.id.llm_input_global)
        llmSendGlobal  = findViewById(R.id.llm_send_global)

        llmSendGlobal.isEnabled = false
        llmInputGlobal.hint     = "Select a model (Settings & Model)"

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
                override fun onToken(piece: String) {}
                override fun onComplete(fullResponse: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Done", Toast.LENGTH_SHORT).show()
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

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation drawer
    // ─────────────────────────────────────────────────────────────────────────

    private fun initDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView      = findViewById(R.id.nav_view)
        toggle       = ActionBarDrawerToggle(
            this, drawerLayout, R.string.drawer_open, R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val fragment = when (item.itemId) {
            R.id.nav_load_project -> { directoryPicker.launch(null); null }
            R.id.nav_file_browser -> FileBrowserFragment.newInstance(projectLoader)
            R.id.nav_llm          -> LLMFragment()
            R.id.nav_settings     -> SettingsFragment()
            else                  -> null
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

    // ─────────────────────────────────────────────────────────────────────────
    // Project loading — builds LLM context from the SAF tree
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleProjectLoad(treeUri: Uri) {
        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Load the file tree for the browser / editor
                projectLoader.loadProject(treeUri)

                // Build a flat content map for LLM context injection
                val (projectName, files) = ProjectContextBuilder.build(this@MainActivity, treeUri)

                withContext(Dispatchers.Main) {
                    appViewModel.setProjectContext(projectName, files)

                    Toast.makeText(
                        this@MainActivity,
                        "Project loaded: $projectName (${files.size} files indexed for LLM)",
                        Toast.LENGTH_SHORT
                    ).show()

                    supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.fragment_container,
                            FileBrowserFragment.newInstance(projectLoader)
                        )
                        .commit()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Project load error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Load failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Floating button
    // ─────────────────────────────────────────────────────────────────────────

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
                    downX = event.rawX;    downY = event.rawY
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
                when {
                    drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        drawerLayout.closeDrawer(GravityCompat.START)
                    supportFragmentManager.backStackEntryCount > 0 ->
                        supportFragmentManager.popBackStack()
                    else -> finish()
                }
            }
        })
    }
}
