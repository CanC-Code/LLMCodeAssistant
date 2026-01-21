// Updated app/src/main/java/io/canccode/aca/MainActivity.kt
package io.canccode.aca

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
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
import androidx.fragment.app.Fragment
import com.llmassistant.utils.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "MainActivity"
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var floatingMenuButton: ImageView
    private lateinit var llmInputGlobal: EditText
    private lateinit var llmSendGlobal: Button
    
    private val projectLoader = ProjectLoader(this)
    private var llmInitialized = false
    
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
            
            initDrawer()
            initFloatingButton()
            initGlobalLLM()
            initLLM()  // New: Initialize LLM with download if needed
            
            // Load initial fragment
            if (savedInstanceState == null) {
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
        }
    }

    private fun initFloatingButton() {
        try {
            floatingMenuButton = findViewById(R.id.floatingMenuButton)
            enableDragAndClick(floatingMenuButton)
            Log.d(TAG, "Floating button initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing floating button", e)
        }
    }

    private fun initGlobalLLM() {
        try {
            llmInputGlobal = findViewById(R.id.llm_input_global)
            llmSendGlobal = findViewById(R.id.llm_send_global)
            
            llmSendGlobal.setOnClickListener {
                val prompt = llmInputGlobal.text.toString().trim()
                if (prompt.isNotEmpty()) {
                    sendToLLM(prompt)
                    llmInputGlobal.text.clear()
                }
            }
            
            Log.d(TAG, "Global LLM interface initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing global LLM", e)
        }
    }

    // New: Initialize LLM, download model if not present
    private fun initLLM() {
        val downloader = ModelDownloader(this)
        val filename = "mistral-7b-instruct-v0.2.Q4_K_M.gguf"
        val modelFile = downloader.getModel(filename)

        if (modelFile != null) {
            val success = LlamaBridge.initNative(modelFile.absolutePath, 512)  // 512 context size
            if (success) {
                setLLMInitialized(true)
                Toast.makeText(this, "LLM initialized successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to initialize LLM", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Prompt user to download ~4GB model
            AlertDialog.Builder(this)
                .setTitle("Download LLM Model")
                .setMessage("The app needs to download a 4.37 GB AI model to enable on-device LLM features. This will run entirely offline after download. Proceed?")
                .setPositiveButton("Yes") { _, _ ->
                    downloadModel(downloader, filename)
                }
                .setNegativeButton("No") { _, _ ->
                    Toast.makeText(this, "LLM features disabled", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    // New: Download the model in background
    private fun downloadModel(downloader: ModelDownloader, filename: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf"
                val expectedSha = "3e0039fd0273fcbebb49228943b17831aadd55cbcbf56f0af00499be2040ccf9"

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Starting model download...", Toast.LENGTH_LONG).show()
                }

                downloader.downloadModel(
                    modelUrl = url,
                    filename = filename,
                    expectedSha256 = expectedSha,
                    onProgress = { progress ->
                        // Optional: Update UI progress here if you add a ProgressBar
                        Log.d(TAG, "Download progress: $progress%")
                    }
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Model downloaded successfully", Toast.LENGTH_LONG).show()
                    initLLM()  // Retry initialization after download
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendToLLM(prompt: String) {
        if (!llmInitialized) {
            Toast.makeText(this, "LLM not initialized yet", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = LlamaBridge.generateNative(prompt, 256)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, response, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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
        }
        return true
    }

    private fun handleProjectLoad(treeUri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            
            Toast.makeText(this, "Loading project...", Toast.LENGTH_SHORT).show()
            
            Thread {
                try {
                    projectLoader.loadProject(treeUri)
                    runOnUiThread {
                        Toast.makeText(this, "Project loaded", Toast.LENGTH_SHORT).show()
                        loadFragment(FileBrowserFragment.newInstance(projectLoader))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading project", e)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading fragment", e)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (toggle.onOptionsItemSelected(item)) {
            true
        } else {
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
        }
    }

    fun getProjectLoader(): ProjectLoader = projectLoader
    
    fun setLLMInitialized(initialized: Boolean) {
        llmInitialized = initialized
        runOnUiThread {
            llmSendGlobal.isEnabled = initialized
            llmInputGlobal.isEnabled = initialized
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (llmInitialized) {
            LlamaBridge.shutdownNative()
        }
    }
}