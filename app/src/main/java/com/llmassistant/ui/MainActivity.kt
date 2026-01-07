// File: LLMCodeAssistant/app/src/main/java/io/canccode/aca/MainActivity.kt
// Author: CCVO
// Purpose: Main activity managing project folder, file browser, editor, and LLM interface with chunked LLM input integration

package io.canccode.aca

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.commit
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var editorContainer: FrameLayout
    private lateinit var llmHandler: LLMHandler
    private lateinit var threadPool: ThreadPoolManager
    private lateinit var fileManager: FileManager
    private lateinit var prefs: SharedPreferences

    private lateinit var llmInputField: EditText
    private lateinit var sendButton: ImageButton

    private var projectFolder: java.io.File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        editorContainer = findViewById(R.id.editor_container)
        llmInputField = findViewById(R.id.llm_input_field)
        sendButton = findViewById(R.id.fab_send)

        prefs = getSharedPreferences("LLMPreferences", MODE_PRIVATE)
        val lastFolderPath = prefs.getString("last_project_folder", null)

        // Initialize FileManager
        fileManager = FileManager()

        // Load last project folder if exists
        projectFolder = lastFolderPath?.let { java.io.File(it) }?.takeIf { it.exists() }

        if (projectFolder == null) {
            promptSelectProjectFolder()
        } else {
            openFileBrowser(projectFolder!!)
        }

        // Initialize LLM
        llmHandler = LLMHandler(this)
        threadPool = ThreadPoolManager()

        setupNavigationMenu()
        setupLLMInput()
    }

    // -----------------------------
    // UI & Navigation
    // -----------------------------
    private fun setupNavigationMenu() {
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
        val editor = supportFragmentManager.findFragmentByTag("editor") as? CodeEditorFragment
        editor?.toggleLineWrap()
    }

    private fun setThemeDark() {
        Toast.makeText(this, "Dark theme applied", Toast.LENGTH_SHORT).show()
    }

    // -----------------------------
    // File Browser
    // -----------------------------
    private fun promptSelectProjectFolder() {
        Toast.makeText(this, "Please select a project folder", Toast.LENGTH_LONG).show()
    }

    private fun openFileBrowser(folder: java.io.File) {
        projectFolder = folder
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
    fun openFileInEditor(file: java.io.File) {
        val editor = CodeEditorFragment.newInstance(file.absolutePath, projectFolder?.absolutePath)
        supportFragmentManager.commit {
            replace(R.id.editor_container, editor, "editor")
        }

        val llmPanel = supportFragmentManager.findFragmentByTag("llm") ?: OutputConsoleFragment.newInstance()
        supportFragmentManager.commit {
            if (!llmPanel.isAdded) replace(R.id.llm_container, llmPanel, "llm")
        }
    }

    fun checkFileWithinProject(file: java.io.File): Boolean {
        val projectPath = projectFolder?.canonicalPath ?: return false
        val filePath = file.canonicalPath
        return filePath.startsWith(projectPath)
    }

    fun indicateOutsideProject(isOutside: Boolean, view: View) {
        if (isOutside) {
            view.setBackgroundColor(Color.parseColor("#33FF0000"))
        } else {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    // -----------------------------
    // LLM Communication
    // -----------------------------
    private fun setupLLMInput() {
        val consoleFragment = supportFragmentManager.findFragmentByTag("llm") as? OutputConsoleFragment
            ?: OutputConsoleFragment.newInstance().also { fragment ->
                supportFragmentManager.commit {
                    replace(R.id.llm_container, fragment, "llm")
                }
            }

        // Send button click
        sendButton.setOnClickListener {
            val inputText = llmInputField.text.toString().trim()
            if (inputText.isNotEmpty()) {
                consoleFragment.appendOutput(inputText, OutputConsoleFragment.MessageType.USER)
                llmInputField.text.clear()

                sendLLMInput(inputText) { response, chunkIndex ->
                    val outputText = if (chunkIndex != null) "[Chunk $chunkIndex]\n$response" else response
                    consoleFragment.appendOutput(outputText, OutputConsoleFragment.MessageType.LLM)
                }
            }
        }

        // Enter inserts newline (multi-line input)
        llmInputField.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                llmInputField.append("\n")
                return@setOnKeyListener true
            }
            false
        }
    }

    /**
     * Sends user input to the LLM.
     * Includes current editor chunk and returns optional chunk index.
     */
    fun sendLLMInput(userInput: String, onResult: (String, Int?) -> Unit) {
        val editorFragment = supportFragmentManager.findFragmentByTag("editor") as? CodeEditorFragment
        val currentChunk = editorFragment?.getCurrentChunk() ?: ""
        val chunkIndex = editorFragment?.currentChunkIndex // Optional: you need to track this in CodeEditorFragment
        threadPool.submit {
            val response = llmHandler.infer("$currentChunk\n$userInput", maxTokens = 512)
            runOnUiThread { onResult(response, chunkIndex) }
        }
    }
}