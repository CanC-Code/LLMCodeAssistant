// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/CodeEditorFragment.kt
// Author: CCVO
// Purpose: Displays and edits a file with syntax highlighting, line numbers, and LLM integration

package com.llmassistant.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.fragment.app.Fragment
import com.llmassistant.R
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import java.io.File

class CodeEditorFragment : Fragment() {

    companion object {
        private const val ARG_FILE_PATH = "file_path"
        private const val ARG_PROJECT_PATH = "project_path"

        fun newInstance(filePath: String, projectPath: String?): CodeEditorFragment {
            val fragment = CodeEditorFragment()
            val args = Bundle()
            args.putString(ARG_FILE_PATH, filePath)
            args.putString(ARG_PROJECT_PATH, projectPath)
            fragment.arguments = args
            return fragment
        }

        // Chunk size for LLM inference (lines)
        const val CHUNK_SIZE = 500
    }

    private var filePath: String? = null
    private var projectPath: String? = null
    private var fileContent: List<String> = emptyList()
    private lateinit var editor: EditText
    private var lineWrapEnabled: Boolean = true
    private var currentChunkIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = arguments?.getString(ARG_FILE_PATH)
        projectPath = arguments?.getString(ARG_PROJECT_PATH)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Using a simple EditText wrapped in a ScrollView for demo; can replace with advanced editor later
        val root = inflater.inflate(R.layout.fragment_code_editor, container, false)
        editor = root.findViewById(R.id.editor_edit_text)
        editor.setHorizontallyScrolling(!lineWrapEnabled)
        editor.isVerticalScrollBarEnabled = true
        editor.isHorizontalScrollBarEnabled = true

        loadFileContent()

        // Watch text changes for future LLM integration
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                fileContent = editor.text.toString().lines()
            }
        })

        return root
    }

    private fun loadFileContent() {
        filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                fileContent = file.readLines()
                editor.setText(fileContent.joinToString("\n"))
            }
        }
    }

    // -----------------------------
    // Public API for MainActivity / LLM
    // -----------------------------

    fun toggleLineWrap() {
        lineWrapEnabled = !lineWrapEnabled
        editor.setHorizontallyScrolling(!lineWrapEnabled)
    }

    fun getCurrentChunk(): String {
        if (fileContent.isEmpty()) return ""

        val startLine = currentChunkIndex * CHUNK_SIZE
        val endLine = minOf(startLine + CHUNK_SIZE, fileContent.size)
        return fileContent.subList(startLine, endLine).joinToString("\n")
    }

    fun moveToNextChunk() {
        val maxIndex = (fileContent.size - 1) / CHUNK_SIZE
        if (currentChunkIndex < maxIndex) currentChunkIndex++
    }

    fun moveToPreviousChunk() {
        if (currentChunkIndex > 0) currentChunkIndex--
    }

    fun getSelectedText(): String {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        return editor.text.substring(start, end)
    }

    fun insertTextAtCursor(text: String) {
        val start = editor.selectionStart
        editor.text.insert(start, text)
    }

    fun replaceSelectedText(text: String) {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        editor.text.replace(start, end, text)
    }
}