package io.canccode.aca

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var projectLoader: ProjectLoader
    private lateinit var llmInputGlobal: EditText
    private lateinit var llmSendGlobal: Button
    
    private var isModelLoaded = false
    private var currentModelPath: String? = null

    private val openTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            projectLoader.loadProject(it)
            Toast.makeText(this, "Project loaded", Toast.LENGTH_SHORT).show()
            
            // Switch to file browser
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
                .commit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)
        
        setupDrawer()
        setupGlobalLLMBar()
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }

        // Auto-load model if previously selected
        autoLoadModel()
    }

    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, 
            R.string.drawer_open, 
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)

        // Floating menu button
        findViewById<ImageView>(R.id.floatingMenuButton).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupGlobalLLMBar() {
        llmInputGlobal = findViewById(R.id.llm_input_global)
        llmSendGlobal = findViewById(R.id.llm_send_global)

        llmSendGlobal.setOnClickListener {
            val input = llmInputGlobal.text.toString().trim()
            if (input.isEmpty()) {
                return@setOnClickListener
            }

            if (!isModelLoaded) {
                Toast.makeText(this, "Please load a model first (Settings)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Send to current LLM fragment if visible
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is LLMFragment) {
                currentFragment.processUserInput(input)
                llmInputGlobal.text.clear()
            }
        }
    }

    private fun autoLoadModel() {
        val prefs = getSharedPreferences("model_prefs", MODE_PRIVATE)
        val path = prefs.getString("selected_model_path", null)
        
        if (path.isNullOrEmpty()) {
            return
        }

        currentModelPath = path
        
        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || !file.canRead()) {
                withContext(Dispatchers.Main) {
                    isModelLoaded = false
                    currentModelPath = null
                }
                return@launch
            }

            val success = try {
                LlamaBridge.initNative(file.absolutePath, 2048)
            } catch (t: Throwable) {
                false
            }

            withContext(Dispatchers.Main) {
                isModelLoaded = success
                if (success) {
                    Toast.makeText(
                        this@MainActivity,
                        "Model loaded: ${file.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    currentModelPath = null
                }
            }
        }
    }

    fun onModelSelectionChanged() {
        // Shutdown current model
        if (isModelLoaded) {
            LlamaBridge.shutdownNative()
            isModelLoaded = false
        }
        
        currentModelPath = null
        
        // Reload if a new model is selected
        autoLoadModel()
    }

    fun getProjectLoader(): ProjectLoader = projectLoader
    
    fun isModelReady(): Boolean = isModelLoaded

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_load_project -> {
                openTreeLauncher.launch(null)
            }
            R.id.nav_file_browser -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
                    .commit()
            }
            R.id.nav_llm -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, LLMFragment())
                    .commit()
            }
            R.id.nav_settings -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SettingsFragment())
                    .commit()
            }
        }
        
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isModelLoaded) {
            LlamaBridge.shutdownNative()
        }
    }
}