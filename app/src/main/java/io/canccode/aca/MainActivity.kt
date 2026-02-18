package io.canccode.aca

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.*
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

    val appViewModel: AppViewModel by viewModels()
    private val projectLoader = ProjectLoader(this)

    // ── LlmService binding ────────────────────────────────────────────────────
    private var llmService: LlmService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
            llmService = (iBinder as LlmService.LocalBinder).getService()
            serviceBound = true
            Log.i(TAG, "LlmService bound")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            llmService = null
        }
    }

    /** Expose service to fragments so they can call generate(). */
    fun getLlmService(): LlmService? = llmService

    // ── SAF pickers ───────────────────────────────────────────────────────────
    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { handleProjectLoad(it) } }

    override fun getProjectLoader(): ProjectLoader = projectLoader

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initDrawer()
        initFloatingButton()
        initGlobalLLMInputs()
        setupBackPressed()
        observeViewModel()

        // Bind to (and start) the foreground service
        val serviceIntent = LlmService.startAndBind(this)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        autoReloadModel()
        autoRestoreProject()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!serviceBound) {
            bindService(
                LlmService.startAndBind(this),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    // ── Auto-restore on cold start ────────────────────────────────────────────

    private fun autoReloadModel() {
        val prefs     = getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
        val savedPath = prefs.getString(SettingsFragment.KEY_MODEL_PATH, null) ?: return
        val file      = File(savedPath)

        if (!file.exists()) {
            Log.w(TAG, "Saved model gone — clearing pref")
            prefs.edit().remove(SettingsFragment.KEY_MODEL_PATH).apply()
            return
        }

        Log.i(TAG, "Auto-reloading model: ${file.name}")
        llmInputGlobal.hint = "Loading model…"

        lifecycleScope.launch(Dispatchers.IO) {
            LlamaBridge.shutdown()
            val ok = LlamaBridge.init(savedPath, 4096)
            withContext(Dispatchers.Main) {
                if (ok) {
                    appViewModel.setModelLoaded(savedPath)
                    Toast.makeText(this@MainActivity, "Model ready: ${file.name}", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().remove(SettingsFragment.KEY_MODEL_PATH).apply()
                    Toast.makeText(this@MainActivity, "Saved model could not load — please re-select", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * If the user loaded a project last session, the SAF persistable permission
     * is still held. Rebuild the project context silently in the background.
     */
    private fun autoRestoreProject() {
        val savedUri = appViewModel.projectUri.value ?: return
        Log.i(TAG, "Auto-restoring project from $savedUri")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                projectLoader.loadProject(savedUri)
                val (name, files) = ProjectContextBuilder.build(this@MainActivity, savedUri)
                withContext(Dispatchers.Main) {
                    appViewModel.setProjectContext(name, savedUri, files)
                    Log.i(TAG, "Project restored: $name (${files.size} files)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Project restore failed", e)
                withContext(Dispatchers.Main) {
                    appViewModel.clearProjectContext()
                }
            }
        }
    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    private fun observeViewModel() {
        appViewModel.isModelLoaded.observe(this) { loaded ->
            llmSendGlobal.isEnabled = loaded
            llmInputGlobal.hint = if (loaded) "Ask the Assistant…"
                                  else         "Select a model (Settings & Model)"
        }
    }

    // ── SettingsFragment callbacks ────────────────────────────────────────────

    override fun onModelSelectionChanged(modelFile: File?) {
        Log.i(TAG, if (modelFile != null) "Model ready: ${modelFile.name}" else "Model cleared")
    }

    override fun onThemeChanged(isDarkMode: Boolean) {}

    // ── Global LLM input bar ──────────────────────────────────────────────────

    private fun initGlobalLLMInputs() {
        llmInputGlobal = findViewById(R.id.llm_input_global)
        llmSendGlobal  = findViewById(R.id.llm_send_global)

        llmSendGlobal.isEnabled = false
        llmInputGlobal.hint     = "Select a model (Settings & Model)"

        llmSendGlobal.setOnClickListener {
            val prompt = llmInputGlobal.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener
            llmInputGlobal.text.clear()

            // Route the prompt through the shared ViewModel so LLMFragment picks it up
            appViewModel.sendToLLM(prompt)

            // Navigate to LLMFragment if not already visible
            val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (current !is LLMFragment) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, LLMFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

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

    // ── Project load ──────────────────────────────────────────────────────────

    private fun handleProjectLoad(treeUri: Uri) {
        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                projectLoader.loadProject(treeUri)
                val (name, files) = ProjectContextBuilder.build(this@MainActivity, treeUri)

                withContext(Dispatchers.Main) {
                    appViewModel.setProjectContext(name, treeUri, files)
                    Toast.makeText(
                        this@MainActivity,
                        "Project: $name (${files.size} files indexed)",
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
                    Toast.makeText(this@MainActivity, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Floating button ───────────────────────────────────────────────────────

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