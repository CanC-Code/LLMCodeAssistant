// File: app/src/main/java/io/canccode/aca/MainActivity.kt
package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var llmHandler: LLMHandler

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var floatingMenuButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate UI FIRST so fragments & touch handling work
        setContentView(R.layout.activity_main)

        // -----------------------------
        // Initialize drawer & hamburger
        // -----------------------------
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.drawer_open, R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // -----------------------------
        // Floating menu button
        // -----------------------------
        floatingMenuButton = findViewById(R.id.floatingMenuButton)
        floatingMenuButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(navView)) {
                drawerLayout.closeDrawer(navView)
            } else {
                drawerLayout.openDrawer(navView)
            }
        }

        // -----------------------------
        // Initialize LLM handler
        // -----------------------------
        llmHandler = LLMHandler(this)

        // Initialize LLM asynchronously (NO permissions required)
        initializeLLM()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (drawerLayout.isDrawerOpen(navView)) {
            drawerLayout.closeDrawer(navView)
            true
        } else {
            drawerLayout.openDrawer(navView)
            true
        }
    }

    private fun initializeLLM() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Initializing LLM…")
                llmHandler.ensureModelReady()
                Log.i(TAG, "LLM initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize LLM", e)
            }
        }
    }

    // Optional: force keyboard to open when focusing LLM input
    fun focusLLMInput(editText: EditText) {
        editText.requestFocus()
        val imm = getSystemService<InputMethodManager>()
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
}