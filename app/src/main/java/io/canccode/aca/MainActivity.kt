package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // Public so fragments can access shared project state
    lateinit var projectLoader: ProjectLoader

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        // Setup hamburger toggle
        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Mode buttons
        val btnEditor = findViewById<Button>(R.id.btnEditorMode)
        val btnLLM = findViewById<Button>(R.id.btnLLMMode)
        val btnLoadProject = findViewById<Button>(R.id.btnLoadProject)

        btnEditor.setOnClickListener { openFragment(EditorFragment()) }
        btnLLM.setOnClickListener { openFragment(LLMFragment()) }
        btnLoadProject.setOnClickListener { pickProjectFolder() }

        // Default screen
        if (savedInstanceState == null) {
            openFragment(EditorFragment())
        }
    }

    // Handle toolbar/hamburger click
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .commit()
    }

    // -------------------------
    // Project folder selection
    // -------------------------
    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                projectLoader.loadProject(it)
            }
        }

    fun pickProjectFolder() {
        folderPickerLauncher.launch(null)
    }

    // -------------------------
    // Navigation drawer handling
    // -------------------------
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_files -> {
                openFragment(FileBrowserFragment())
            }
            R.id.nav_editor -> {
                openFragment(EditorFragment())
            }
            R.id.nav_console -> {
                openFragment(LLMFragment())
            }
            R.id.nav_reload_model -> {
                Toast.makeText(this, "Reloading model...", Toast.LENGTH_SHORT).show()
                // Add your reload model logic here
            }
            R.id.nav_clear_console -> {
                Toast.makeText(this, "Clearing console...", Toast.LENGTH_SHORT).show()
                // Add your clear console logic here
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
}