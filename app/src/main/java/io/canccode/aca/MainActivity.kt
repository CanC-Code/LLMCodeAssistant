package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    lateinit var projectLoader: ProjectLoader
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var floatingMenuButton: ImageView

    // LLM
    private lateinit var llmHandler: LLMHandler
    private var llmProgressBar: ProgressBar? = null

    private var dX = 0f
    private var dY = 0f
    private var isDragging = false

    private var currentModeFragment: Fragment = EditorFragment()
    private val llmFragment = LLMFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)
        llmHandler = LLMHandler(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        fragmentContainer = findViewById(R.id.fragment_container)
        floatingMenuButton = findViewById(R.id.floatingMenuButton)

        llmProgressBar = findViewById<ProgressBar?>(R.id.llm_progress_bar)

        setupFloatingMenu()

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, currentModeFragment)
                add(R.id.llm_container, llmFragment)
            }
        }

        // ---------- LLM initialization on launch ----------
        lifecycleScope.launch {
            try {
                llmProgressBar?.visibility = ProgressBar.VISIBLE
                val ok = llmHandler.initialize { progress ->
                    runOnUiThread {
                        llmProgressBar?.progress = progress
                    }
                }

                runOnUiThread {
                    if (ok) {
                        Log.i(TAG, "LLM ready")
                        Toast.makeText(this@MainActivity, "LLM ready", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "LLM failed to initialize")
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to initialize LLM",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    llmProgressBar?.visibility = ProgressBar.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing LLM", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error initializing LLM: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    llmProgressBar?.visibility = ProgressBar.GONE
                }
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
                    if (abs(v.x - newX) > 10 || abs(v.y - newY) > 10) isDragging = true
                    v.x = newX
                    v.y = newY
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) drawerLayout.openDrawer(GravityCompat.START)
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
            R.id.nav_llm ->
                Toast.makeText(this, "LLM is always available", Toast.LENGTH_SHORT).show()
            R.id.nav_load_project -> pickProjectFolder()
            R.id.nav_reload_model -> {
                Toast.makeText(this, "Reloading model...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    try {
                        llmProgressBar?.visibility = ProgressBar.VISIBLE
                        llmHandler.close()
                        llmHandler.initialize { progress ->
                            runOnUiThread {
                                llmProgressBar?.progress = progress
                            }
                        }
                        runOnUiThread { Toast.makeText(this@MainActivity, "Model reloaded", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reload model", e)
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Failed to reload model", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        runOnUiThread { llmProgressBar?.visibility = ProgressBar.GONE }
                    }
                }
            }
            R.id.nav_clear_console ->
                Toast.makeText(this, "Clearing console...", Toast.LENGTH_SHORT).show()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun switchMode(fragment: Fragment) {
        currentModeFragment = fragment
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
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