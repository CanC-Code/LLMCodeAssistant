package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.navigation.NavigationView
import kotlin.math.abs

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var projectLoader: ProjectLoader
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var floatingMenuButton: ImageView

    private var dX = 0f
    private var dY = 0f
    private var isDragging = false

    private var currentModeFragment: Fragment = EditorFragment()
    private val llmFragment = LLMFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        fragmentContainer = findViewById(R.id.fragment_container)
        floatingMenuButton = findViewById(R.id.floatingMenuButton)

        setupFloatingMenu()

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                // Add main mode fragment
                replace(R.id.fragment_container, currentModeFragment)
                // Add LLM fragment to its own container
                add(R.id.llm_container, llmFragment)
            }
        }
    }

    private fun setupFloatingMenu() {
        floatingMenuButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX + dX).coerceIn(0f, drawerLayout.width - v.width.toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, drawerLayout.height - v.height.toFloat())
                    if (abs(v.x - newX) > 10 || abs(v.y - newY) > 10) {
                        isDragging = true
                    }
                    v.x = newX
                    v.y = newY
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        drawerLayout.openDrawer(GravityCompat.START)
                    }
                }
            }
            true
        }
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                projectLoader.loadProject(it)
            }
        }

    fun pickProjectFolder() {
        folderPickerLauncher.launch(null)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_files -> switchMode(FileBrowserFragment())
            R.id.nav_editor -> switchMode(EditorFragment())
            R.id.nav_llm -> Toast.makeText(this, "LLM is always available", Toast.LENGTH_SHORT).show()
            R.id.nav_load_project -> pickProjectFolder()
            R.id.nav_reload_model -> Toast.makeText(this, "Reloading model...", Toast.LENGTH_SHORT).show()
            R.id.nav_clear_console -> Toast.makeText(this, "Clearing console...", Toast.LENGTH_SHORT).show()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun switchMode(fragment: Fragment) {
        currentModeFragment = fragment
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
            // no longer touching llmFragment
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}