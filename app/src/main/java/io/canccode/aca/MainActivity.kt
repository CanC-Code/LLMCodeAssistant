package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var projectLoader: ProjectLoader
    
    private lateinit var llmInputGlobal: EditText
    private lateinit var llmSendGlobal: Button
    private lateinit var floatingMenuButton: ImageView
    
    private var currentFragment: androidx.fragment.app.Fragment? = null
    private var llmFragment: LLMFragment? = null

    private val pickProjectLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleProjectSelection(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)
        
        setupDrawer()
        setupBottomBar()
        setupFloatingMenu()

        if (savedInstanceState == null) {
            // Start with LLM fragment
            showLLMFragment()
        }

        autoInitModel()
    }

    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }

    private fun setupBottomBar() {
        llmInputGlobal = findViewById(R.id.llm_input_global)
        llmSendGlobal = findViewById(R.id.llm_send_global)

        llmSendGlobal.setOnClickListener {
            val input = llmInputGlobal.text.toString().trim()
            if (input.isNotEmpty()) {
                sendToLLM(input)
                llmInputGlobal.text.clear()
            }
        }
    }

    private fun setupFloatingMenu() {
        floatingMenuButton = findViewById(R.id.floatingMenuButton)
        floatingMenuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun sendToLLM(input: String) {
        // Ensure LLM fragment exists and is in chat history
        if (llmFragment == null) {
            llmFragment = LLMFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, llmFragment!!, "llm_fragment")
                .hide(llmFragment!!)
                .commit()
        }

        // Add to chat history
        llmFragment?.addUserMessage(input)
        
        // Show toast confirmation
        Toast.makeText(this, "Sent to LLM", Toast.LENGTH_SHORT).show()
    }

    private fun showLLMFragment() {
        if (llmFragment == null) {
            llmFragment = LLMFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, llmFragment!!, "llm_fragment")
                .commit()
        } else {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, llmFragment!!)
                .commit()
        }
        currentFragment = llmFragment
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_load_project -> {
                pickProjectLauncher.launch(null)
            }
            R.id.nav_file_browser -> {
                val fragment = FileBrowserFragment.newInstance(projectLoader)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
                currentFragment = fragment
            }
            R.id.nav_llm -> {
                showLLMFragment()
            }
            R.id.nav_settings -> {
                val fragment = SettingsFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
                currentFragment = fragment
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun handleProjectSelection(uri: Uri) {
        // Persist access
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, takeFlags)

        Toast.makeText(this, "Loading project...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                projectLoader.loadProject(uri)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Project loaded successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Navigate to file browser
                    val fragment = FileBrowserFragment.newInstance(projectLoader)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error loading project: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun autoInitModel() {
        val prefs = getSharedPreferences("model_prefs", MODE_PRIVATE)
        val path = prefs.getString(SettingsFragment.KEY_MODEL_PATH, null) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                val f = File(path)
                f.exists() && f.canRead() && LlamaBridge.initNative(f.absolutePath, 2048)
            } catch (t: Throwable) {
                false
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    if (ok) "✓ Model ready" else "✗ Model load failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun onModelSelectionChanged() {
        // Reinitialize model
        autoInitModel()
    }

    fun getProjectLoader(): ProjectLoader = projectLoader

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}