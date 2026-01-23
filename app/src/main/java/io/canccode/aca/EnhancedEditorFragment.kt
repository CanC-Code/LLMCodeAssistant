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
    private var fileUriString: String = ""
    private var projectLoader: ProjectLoader? = null   // now nullable & optional

    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()
    private var currentContent = ""
    private var isUndoRedoOperation = false

    companion object {
        private const val ARG_FILE_PATH = "file_path"
        private const val ARG_FILE_URI = "file_uri"

        // No longer requires ProjectLoader – can be null / fallback
        fun newInstance(
            path: String,
            uri: String = "",
            loader: ProjectLoader? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        arguments?.let {
            filePath = it.getString(ARG_FILE_PATH, "")
            fileUriString = it.getString(ARG_FILE_URI, "")
        }

        // Optional: try to get from activity if still exists in future versions
        if (projectLoader == null) {
            projectLoader = (activity as? MainActivity)?.getProjectLoader()
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
        // Dark theme styling
        editorView.setBackgroundColor(Color.parseColor("#1E1E1E"))
        editorView.setTextColor(Color.parseColor("#D4D4D4"))
        editorView.textSize = 14f
        editorView.setHorizontallyScrolling(true)

        lineNumbersView.setBackgroundColor(Color.parseColor("#252525"))
        lineNumbersView.setTextColor(Color.parseColor("#858585"))
        lineNumbersView.textSize = 14f
        lineNumbersView.setPadding(16, 0, 16, 0)

        // Line numbers & undo/redo tracking
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

                        // Prevent memory explosion
                        if (undoStack.size > 80) {
                            undoStack.removeAt(0)
                        }
                    }
                }
            }
        })
    }

    private fun loadFileContent() {
        val content = when {
            fileUriString.isNotBlank() -> {
                try {
                    val uri = Uri.parse(fileUriString)
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    } ?: ""
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
                    ""
                }
            }
            filePath.isNotBlank() && projectLoader != null -> {
                try {
                    projectLoader!!.getFileContent(filePath)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Project file error: ${e.message}", Toast.LENGTH_SHORT).show()
                    ""
                }
            }
            else -> {
                Toast.makeText(requireContext(), "No file path or project loaded", Toast.LENGTH_SHORT).show()
                "// Empty file\n"
            }
        }

        currentContent = content
        undoStack.clear()
        undoStack.push(content)  // initial state for undo
        redoStack.clear()
        editorView.setText(content)
        editorView.setSelection(0)
        updateLineNumbers()
    }

    private fun updateLineNumbers() {
        val text = editorView.text.toString()
        val lineCount = text.lines().size.coerceAtLeast(1)
        val numbers = buildString {
            for (i in 1..lineCount) {
                append(i).append("\n")
            }
        }
        lineNumbersView.text = numbers
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.editor_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveFile()
                true
            }
            R.id.action_save_as -> {
                Toast.makeText(requireContext(), "Save As - not implemented yet", Toast.LENGTH_SHORT).show()
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
        val content = editorView.text.toString()

        when {
            fileUriString.isNotBlank() -> {
                try {
                    val uri = Uri.parse(fileUriString)
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray())
                    }
                    Toast.makeText(requireContext(), "File saved via URI", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            filePath.isNotBlank() && projectLoader != null -> {
                try {
                    projectLoader!!.updateFile(filePath, content)
                    Toast.makeText(requireContext(), "File updated in project", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Project save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Toast.makeText(requireContext(), "No valid save location (no URI or project)", Toast.LENGTH_LONG).show()
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
            updateLineNumbers()
        } else {
            Toast.makeText(requireContext(), "Nothing to undo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRedo() {
        if (redoStack.isNotEmpty()) {
            isUndoRedoOperation = true
            val next = redoStack.pop()
            undoStack.push(next)
            currentContent = next
            editorView.setText(currentContent)
            editorView.setSelection(currentContent.length)
            isUndoRedoOperation = false
            updateLineNumbers()
        } else {
            Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show()
        }
    }
}