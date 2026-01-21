package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.fragment.app.Fragment
import kotlin.math.abs

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "MainActivity"
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var floatingMenuButton: ImageView
    
    private val projectLoader = ProjectLoader(this)
    
    // SAF directory picker
    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleProjectLoad(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d(TAG, "Setting content view")
            setContentView(R.layout.activity_main)
            
            Log.d(TAG, "Initializing drawer")
            initDrawer()
            
            Log.d(TAG, "Initializing floating button")
            initFloatingButton()
            
            // Load initial fragment
            if (savedInstanceState == null) {
                Log.d(TAG, "Loading initial fragment")
                loadFragment(LLMFragment())
            }
            
            Log.d(TAG, "MainActivity onCreate complete")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initDrawer() {
        try {
            drawerLayout = findViewById(R.id.drawer_layout)
            navView = findViewById(R.id.nav_view)
            
            toggle = ActionBarDrawerToggle(
                this, 
                drawerLayout, 
                R.string.drawer_open, 
                R.string.drawer_close
            )
            drawerLayout.addDrawerListener(toggle)
            toggle.syncState()
            
            navView.setNavigationItemSelectedListener(this)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            
            Log.d(TAG, "Drawer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing drawer", e)
            Toast.makeText(this, "Drawer error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initFloatingButton() {
        try {
            floatingMenuButton = findViewById(R.id.floatingMenuButton)
            enableDragAndClick(floatingMenuButton)
            Log.d(TAG, "Floating button initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing floating button", e)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        try {
            when (item.itemId) {
                R.id.nav_load_project -> {
                    directoryPicker.launch(null)
                }
                R.id.nav_file_browser -> {
                    loadFragment(FileBrowserFragment())
                }
                R.id.nav_llm -> {
                    loadFragment(LLMFragment())
                }
                R.id.nav_settings -> {
                    Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
                }
            }
            
            drawerLayout.closeDrawer(GravityCompat.START)
        } catch (e: Exception) {
            Log.e(TAG, "Error in navigation", e)
            Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun handleProjectLoad(treeUri: Uri) {
        try {
            // Persist permissions
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            
            Toast.makeText(this, "Loading project...", Toast.LENGTH_SHORT).show()
            
            Thread {
                try {
                    projectLoader.loadProject(treeUri)
                    runOnUiThread {
                        Toast.makeText(this, "Project loaded successfully", Toast.LENGTH_SHORT).show()
                        loadFragment(FileBrowserFragment.newInstance(projectLoader))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading project", e)
                    runOnUiThread {
                        Toast.makeText(this, "Error loading project: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling project load", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            Log.d(TAG, "Fragment loaded: ${fragment.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading fragment", e)
            Toast.makeText(this, "Fragment error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        try {
            toggle.syncState()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPostCreate", e)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return try {
            if (toggle.onOptionsItemSelected(item)) {
                true
            } else {
                super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in options menu", e)
            super.onOptionsItemSelected(item)
        }
    }

    private fun enableDragAndClick(view: View) {
        var dX = 0f
        var dY = 0f
        var downX = 0f
        var downY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            try {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = v.x - event.rawX
                        dY = v.y - event.rawY
                        downX = event.rawX
                        downY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = abs(event.rawX - downX)
                        val deltaY = abs(event.rawY - downY)
                        
                        // If moved more than 10px, it's a drag
                        if (deltaX > 10 || deltaY > 10) {
                            isDragging = true
                            v.animate()
                                .x(event.rawX + dX)
                                .y(event.rawY + dY)
                                .setDuration(0)
                                .start()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // It was a click, not a drag
                            Log.d(TAG, "Floating button clicked")
                            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                                drawerLayout.closeDrawer(GravityCompat.START)
                            } else {
                                drawerLayout.openDrawer(GravityCompat.START)
                            }
                            v.performClick()
                        }
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in drag handler", e)
                false
            }
        }
    }

    fun getProjectLoader(): ProjectLoader = projectLoader
}