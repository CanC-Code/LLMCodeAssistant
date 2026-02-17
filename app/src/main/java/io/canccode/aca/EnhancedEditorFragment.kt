package io.canccode.aca

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.*

class EnhancedEditorFragment : Fragment() {

    private lateinit var lineNumbersView: TextView
    private lateinit var editorView: EditText
    private lateinit var scrollContainer: HorizontalScrollView

    private var filePath: String = ""
    private var fileUri: String = ""
    private lateinit var projectLoader: ProjectLoader

    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()
    private var currentContent = ""
    private var isUndoRedoOperation = false

    companion object {
        private const val ARG_FILE_PATH = "file_path"
        private const val ARG_FILE_URI = "file_uri"

        fun newInstance(
            path: String, 
            uri: String, 
            loader: ProjectLoader
        ): EnhancedEditorFragment {
            return EnhancedEditorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PATH, path)
                    putString(ARG_FILE_URI, uri)
                }
                projectLoader = loader
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        arguments?.let {
            filePath = it.getString(ARG_FILE_PATH, "")
            fileUri = it.getString(ARG_FILE_URI, "")
        }

        if (!::projectLoader.isInitialized) {
            projectLoader = (activity as? MainActivity)?.getProjectLoader() 
                ?: ProjectLoader(requireContext())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_enhanced_editor, container, false)

        lineNumbersView = root.findViewById(R.id.lineNumbers)
        editorView = root.findViewById(R.id.editorText)
        scrollContainer = root.findViewById(R.id.editorScrollContainer)

        setupEditor()
        loadFileContent()

        return root
    }

    private fun setupEditor() {
        // Editor styling
        editorView.setBackgroundColor(Color.parseColor("#1E1E1E"))
        editorView.setTextColor(Color.parseColor("#D4D4D4"))
        editorView.textSize = 14f
        editorView.setHorizontallyScrolling(true)

        // Line numbers styling
        lineNumbersView.setBackgroundColor(Color.parseColor("#252525"))
        lineNumbersView.setTextColor(Color.parseColor("#858585"))
        lineNumbersView.textSize = 14f
        lineNumbersView.setPadding(16, 0, 16, 0)

        // Sync line numbers with content
        editorView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                updateLineNumbers()

                if (!isUndoRedoOperation) {
                    val newContent = s.toString()
                    if (newContent != currentContent) {
                        undoStack.push(currentContent)
                        redoStack.clear()
                        currentContent = newContent

                        // Limit undo stack size
                        if (undoStack.size > 100) {
                            undoStack.removeAt(0)
                        }
                    }
                }
            }
        })
    }

    private fun loadFileContent() {
        val content = if (fileUri.isNotEmpty()) {
            try {
                val uri = Uri.parse(fileUri)
                requireContext().contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                ""
            }
        } else {
            projectLoader.getFileContent(filePath)
        }

        currentContent = content
        undoStack.push(content)
        editorView.setText(content)
        updateLineNumbers()
    }

    private fun updateLineNumbers() {
        val lines = editorView.text.toString().split("\n")
        val lineNumbers = StringBuilder()

        for (i in 1..lines.size) {
            lineNumbers.append(i).append("\n")
        }

        lineNumbersView.text = lineNumbers.toString()
    }

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.editor_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveFile()
                true
            }
            R.id.action_save_as -> {
                Toast.makeText(requireContext(), "Save As - coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_undo -> {
                performUndo()
                true
            }
            R.id.action_redo -> {
                performRedo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveFile() {
        if (fileUri.isNotEmpty()) {
            try {
                val uri = Uri.parse(fileUri)
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    it.write(editorView.text.toString().toByteArray())
                }
                Toast.makeText(requireContext(), "File saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            val saved = projectLoader.saveFile(filePath, editorView.text.toString())
            if (saved) {
                Toast.makeText(requireContext(), "File saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Error: could not save file", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performUndo() {
        if (undoStack.size > 1) {
            isUndoRedoOperation = true
            redoStack.push(undoStack.pop())
            currentContent = undoStack.peek()
            editorView.setText(currentContent)
            editorView.setSelection(currentContent.length)
            isUndoRedoOperation = false
        } else {
            Toast.makeText(requireContext(), "Nothing to undo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRedo() {
        if (redoStack.isNotEmpty()) {
            isUndoRedoOperation = true
            val content = redoStack.pop()
            undoStack.push(content)
            currentContent = content
            editorView.setText(content)
            editorView.setSelection(currentContent.length)
            isUndoRedoOperation = false
        } else {
            Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show()
        }
    }
}