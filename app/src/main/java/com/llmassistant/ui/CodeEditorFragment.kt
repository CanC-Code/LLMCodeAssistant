// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/CodeEditorFragment.kt
// Author: CCVO
// Purpose: Code editor fragment with line numbers, line wrapping, file loading, and LLM integration

package com.llmassistant.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.llmassistant.editor.ChunkManager
import com.llmassistant.R
import java.io.File

class CodeEditorFragment : Fragment() {

    private var filePath: String? = null
    private var projectRootPath: String? = null

    private lateinit var editorTextView: TextView
    private lateinit var lineNumbersView: TextView
    private lateinit var scrollView: HorizontalScrollView
    private lateinit var wrapToggleButton: Button

    private var lineWrapEnabled = true
    private val chunkManager = ChunkManager()

    companion object {
        private const val ARG_FILE_PATH = "file_path"
        private const val ARG_PROJECT_ROOT = "project_root"

        fun newInstance(filePath: String, projectRoot: String?): CodeEditorFragment {
            val fragment = CodeEditorFragment()
            val args = Bundle()
            args.putString(ARG_FILE_PATH, filePath)
            args.putString(ARG_PROJECT_ROOT, projectRoot)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = arguments?.getString(ARG_FILE_PATH)
        projectRootPath = arguments?.getString(ARG_PROJECT_ROOT)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        lineNumbersView = TextView(requireContext()).apply {
            setTextColor(0xFF888888.toInt())
            setPadding(8)
            gravity = Gravity.TOP or Gravity.END
        }

        editorTextView = TextView(requireContext()).apply {
            setTextColor(0xFF000000.toInt())
            setPadding(8)
            isFocusable = true
            isFocusableInTouchMode = true
            movementMethod = ScrollingMovementMethod()
        }

        scrollView = HorizontalScrollView(requireContext()).apply {
            addView(editorTextView)
        }

        // Line numbers + editor
        layout.addView(lineNumbersView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        layout.addView(scrollView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)

        // Line wrap toggle
        wrapToggleButton = Button(requireContext()).apply {
            text = "Toggle Wrap"
            setOnClickListener { toggleLineWrap() }
        }

        val containerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(layout, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(wrapToggleButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        loadFile()

        return containerLayout
    }

    private fun loadFile() {
        val file = filePath?.let { File(it) } ?: return
        if (!file.exists()) return

        val text = chunkManager.loadFileChunks(file)
        editorTextView.text = text

        updateLineNumbers(text)
    }

    private fun updateLineNumbers(text: String) {
        val lines = text.split("\n")
        val numbers = lines.indices.joinToString("\n") { (it + 1).toString() }
        lineNumbersView.text = numbers
    }

    fun toggleLineWrap() {
        lineWrapEnabled = !lineWrapEnabled
        editorTextView.setHorizontallyScrolling(!lineWrapEnabled)
        Toast.makeText(requireContext(), "Line wrap: $lineWrapEnabled", Toast.LENGTH_SHORT).show()
    }

    /**
     * Returns the currently visible chunk of text (for LLM interaction)
     */
    fun getCurrentChunk(): String {
        return editorTextView.text.toString()
    }
}