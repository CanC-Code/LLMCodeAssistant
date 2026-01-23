package io.canccode.aca

import android.content.Intent
import android.content.SharedPreferences
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
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

    private var currentModelPath: String? = null
    private lateinit var prefs: SharedPreferences

    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { handleProjectLoad(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("model_prefs", MODE_PRIVATE)
        loadLastModelPath()

        initDrawer()
        initFloatingButton()
        initGlobalLLM()
        tryAutoInitLLM()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }
    }

    private fun loadLastModelPath() {
        currentModelPath = prefs.getString("selected_model_path", null)
            ?: prefs.getString("selected_model_uri", null)
        Log.i(TAG, "Loaded model path: $currentModelPath")
    }

    private fun tryAutoInitLLM() {
        val path = currentModelPath ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                if (path.startsWith("content://")) {
                    Log.w(TAG, "content:// not supported yet")
                } else {
                    val file = File(path)
                    if (file.exists() && file.canRead()) {
                        success = LlamaBridge.initNative(file.absolutePath, 2048)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Init failed", e)
            }
            withContext(Dispatchers.Main) {
                llmInitialized = success
                llmSendGlobal.isEnabled = success
                llmInputGlobal.isEnabled = success
                Toast.makeText(this@MainActivity, if (success) "Model ready" else "Model load failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ... rest of initDrawer(), initFloatingButton(), initGlobalLLM() same as before ...

    private fun sendToLLM(prompt: String) {
        if (!llmInitialized) {
            Toast.makeText(this, "LLM not ready", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = LlamaBridge.generateNative(prompt, 256)
                withContext(Dispatchers.Main) {
                    val short = if (response.length > 200) response.substring(0, 200) + "..." else response
                    Toast.makeText(this@MainActivity, short, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_load_project -> directoryPicker.launch(null)
            R.id.nav_file_browser -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FileBrowserFragment.newInstance(projectLoader))
                .commit()
            R.id.nav_llm -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
            R.id.nav_settings -> supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    // handleProjectLoad, loadFragment, setLLMInitialized, enableDragAndClick, onPostCreate, onOptionsItemSelected, onDestroy same as previous versions
}