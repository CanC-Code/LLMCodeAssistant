package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    // Make projectLoader public so fragments can access it
    lateinit var projectLoader: ProjectLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize your ProjectLoader
        projectLoader = ProjectLoader(this)

        // Setup BottomNavigationView
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_editor -> {
                    openFragment(EditorFragment())
                    true
                }
                R.id.nav_llm -> {
                    openFragment(LLMFragment())
                    true
                }
                else -> false
            }
        }

        // Open default fragment
        if (savedInstanceState == null) {
            openFragment(EditorFragment())
        }
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // -------------------------
    // Project folder selection
    // -------------------------
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Grant persistent access
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            // Tell ProjectLoader to load it
            projectLoader.loadProjectFromUri(it)
        }
    }

    fun pickProjectFolder() {
        folderPickerLauncher.launch(null)
    }
}