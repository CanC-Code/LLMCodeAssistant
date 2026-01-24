package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
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
    private lateinit var projectLoader: ProjectLoader
    private var settingsFragment: SettingsFragment? = null

    companion object {
        private const val REQUEST_CODE_OPEN_DIRECTORY = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ProjectLoader
        projectLoader = ProjectLoader(this)

        // Setup drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        // Setup toggle
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Setup floating menu button
        findViewById<ImageView>(R.id.floatingMenuButton).setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Load default fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }

        // Auto-init model if previously selected
        autoInitModel()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_load_project -> {
                openDirectoryPicker()
            }
            R.id.nav_file_browser -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
                    .addToBackStack(null)
                    .commit()
            }
            R.id.nav_llm -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, LLMFragment())
                    .addToBackStack(null)
                    .commit()
            }
            R.id.nav_settings -> {
                val fragment = SettingsFragment()
                settingsFragment = fragment
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // Persist permissions
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Load project
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
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
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
        }
    }

    private fun autoInitModel() {
        val prefs = getSharedPreferences("model_prefs", MODE_PRIVATE)
        val path = prefs.getString(SettingsFragment.KEY_MODEL_PATH, null) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val success = try {
                val file = File(path)
                file.exists() && file.canRead() && LlamaBridge.initNative(file.absolutePath, 2048)
            } catch (t: Throwable) {
                false
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    if (success) "Model ready" else "Model load failed",
                    Toast.LENGTH_LONG
                ).show()
                
                settingsFragment?.onModelInitComplete(success)
            }
        }
    }

    fun onModelSelectionChanged() {
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