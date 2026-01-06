// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/MainActivity.kt
// Author: CCVO
// Purpose: Main activity managing project folder, file browser, editor, and LLM interface

package com.llmassistant.ui

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.commit
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.llmassistant.R
import com.llmassistant.editor.FileManager
import com.llmassistant.llm.LLMHandler
import com.llmassistant.llm.ThreadPoolManager
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var editorContainer: FrameLayout
    private lateinit var llmHandler: LLMHandler
    private lateinit var threadPool: ThreadPoolManager
    private lateinit var fileManager: FileManager
    private lateinit var prefs: SharedPreferences

    private var projectFolder: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        editorContainer = findViewById(R.id.editor_container)

        prefs = getSharedPreferences("LLMPreferences", MODE_PRIVATE)
        val lastFolderPath = prefs.getString("last_project_folder", null)

        // Initialize FileManager
        fileManager = FileManager()

        // Load last project folder if exists
        projectFolder = lastFolderPath?.let { File(it) }?.takeIf { it.exists() }

        if (projectFolder == null) {
            promptSelectProjectFolder()
        } else {
            openFileBrowser(projectFolder!!)
        }

        // Initialize LLM
        llmHandler = LLMHandler(this)
        threadPool = ThreadPoolManager()

        setupNavigationMenu()
    }

    // -----------------------------
    // UI & Navigation
    // -----------------------------
    private fun setupNavigationMenu() {
        // Example QOL items; add toggles/buttons here
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_toggle_wrap -> {
                    toggleLineWrap()
                    true
                }
                R.id.menu_theme_dark -> {
                    setThemeDark()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleLineWrap() {
        // Communicate to editor fragment
        val editor = supportFragmentManager.findFragmentByTag("editor") as? CodeEditorFragment
        editor?.toggleLineWrap()
    }

    private fun setThemeDark() {
        // Communicate to all fragments for theme change
        Toast.makeText(this, "Dark theme applied", Toast.LENGTH_SHORT).show()
    }

    // -----------------------------
    // File Browser
    // -----------------------------
    private fun promptSelectProjectFolder() {
        // TODO: Implement folder picker (Storage Access Framework or File Picker library)
        Toast.makeText(this, "Please select a project folder", Toast.LENGTH_LONG).show()
    }

    private fun openFileBrowser(folder: File) {
        projectFolder = folder
        // Save folder for next session
        prefs.edit { putString("last_project_folder", folder.absolutePath) }

        supportFragmentManager.commit {
            replace(
                R.id.file_browser_container,
                FileBrowserFragment.newInstance(folder.absolutePath),
                "file_browser"
            )
        }
    }

    // -----------------------------
    // Editor & LLM integration
    // -----------------------------
    fun openFileInEditor(file: File) {
        val editor = CodeEditorFragment.newInstance(file.absolutePath, projectFolder?.absolutePath)
        supportFragmentManager.commit {
            replace(R.id.editor_container, editor, "editor")
        }
        // Ensure LLM panel remains visible and live
        val llmPanel = supportFragmentManager.findFragmentByTag("llm") ?: OutputConsoleFragment.newInstance()
        supportFragmentManager.commit {
            if (!llmPanel.isAdded) replace(R.id.llm_container, llmPanel, "llm")
        }
    }

    fun checkFileWithinProject(file: File): Boolean {
        val projectPath = projectFolder?.canonicalPath ?: return false
        val filePath = file.canonicalPath
        return filePath.startsWith(projectPath)
    }

    fun indicateOutsideProject(isOutside: Boolean, view: View) {
        // Red outline if outside project folder
        if (isOutside) {
            view.setBackgroundColor(Color.parseColor("#33FF0000")) // semi-transparent red overlay
        } else {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    // -----------------------------
    // LLM Communication
    // -----------------------------
    fun sendLLMInput(userInput: String, onResult: (String) -> Unit) {
        val currentFile = supportFragmentManager.findFragmentByTag("editor") as? CodeEditorFragment
        val currentChunk = currentFile?.getCurrentChunk() ?: ""
        threadPool.submit {
            val response = llmHandler.infer("$currentChunk\n$userInput", maxTokens = 512)
            runOnUiThread { onResult(response) }
        }
    }

    // Optional: Floating "Send" button for LLM
    private fun setupSendButton() {
        val sendButton = findViewById<FloatingActionButton>(R.id.fab_send)
        sendButton.setOnClickListener {
            val llmInput = "" // retrieve from LLM input field
            sendLLMInput(llmInput) { output ->
                val console = supportFragmentManager.findFragmentByTag("llm") as? OutputConsoleFragment
                console?.appendOutput(output)
            }
        }
    }
}