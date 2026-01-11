package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var projectLoader: ProjectLoader
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var floatingMenu: ImageView

    private var dX = 0f
    private var dY = 0f
    private var isDragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        floatingMenu = findViewById(R.id.floatingMenuButton)
        setupFloatingMenu()

        val btnEditor = findViewById<Button>(R.id.btnEditorMode)
        val btnLLM = findViewById<Button>(R.id.btnLLMMode)
        val btnLoadProject = findViewById<Button>(R.id.btnLoadProject)

        btnEditor.setOnClickListener { openFragment(EditorFragment()) }
        btnLLM.setOnClickListener { openFragment(LLMFragment()) }
        btnLoadProject.setOnClickListener { pickProjectFolder() }

        if (savedInstanceState == null) {
            openFragment(EditorFragment())
        }
    }

    private fun setupFloatingMenu() {
        floatingMenu.setOnTouchListener { v, event ->
            val parentWidth = (v.parent as DrawerLayout).width
            val parentHeight = (v.parent as DrawerLayout).height

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    var newX = event.rawX + dX
                    var newY = event.rawY + dY

                    // Clamp within parent bounds
                    newX = min(max(0f, newX), parentWidth - v.width.toFloat())
                    newY = min(max(0f, newY), parentHeight - v.height.toFloat())

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

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .commit()
    }

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

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_files -> openFragment(FileBrowserFragment())
            R.id.nav_editor -> openFragment(EditorFragment())
            R.id.nav_console -> openFragment(LLMFragment())
            R.id.nav_reload_model ->
                Toast.makeText(this, "Reloading model...", Toast.LENGTH_SHORT).show()
            R.id.nav_clear_console ->
                Toast.makeText(this, "Clearing console...", Toast.LENGTH_SHORT).show()
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
