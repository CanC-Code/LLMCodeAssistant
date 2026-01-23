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
    private var projectLoader: ProjectLoader? = null   // optional / nullable

    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()
    private var currentContent = ""
    private var isUndoRedoOperation = false

    companion object {
        private const val ARG_FILE_PATH = "file_path"
        private const val ARG_FILE_URI = "file_uri"

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

        // No more automatic lookup — if MainActivity doesn't have it, stay null
        // (you can add back later if you restore project logic)
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
        editorView.setBackgroundColor(Color.parseColor("#1E1E1E"))
        editorView.setTextColor(Color.parseColor("#D4D4D4"))
        editorView.textSize = 14f
        editorView.setHorizontallyScrolling(true)

        lineNumbersView.setBackgroundColor(Color.parseColor("#252525"))
        lineNumbersView.setTextColor(Color.parseColor("#858585"))
        lineNumbersView.textSize = 14f
        lineNumbersView.setPadding(16, 0, 16, 0)

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
                        if (undoStack.size > 80) undoStack.removeAt(0)
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
                    requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
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
                "// No file or project loaded\n"
            }
        }

        currentContent = content
        undoStack.clear()
        undoStack.push(content)
        redoStack.clear()
        editorView.setText(content)
        editorView.setSelection(0)
        updateLineNumbers()
    }

    private fun updateLineNumbers() {
        val text = editorView.text.toString()
        val lineCount = text.lines().size.coerceAtLeast(1)
        lineNumbersView.text = buildString {
            repeat(lineCount) { i -> append("${i + 1}\n") }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.editor_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> { saveFile(); true }
            R.id.action_save_as -> {
                Toast.makeText(requireContext(), "Save As - not implemented", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_undo -> { performUndo(); true }
            R.id.action_redo -> { performRedo(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveFile() {
        val content = editorView.text.toString()

        when {
            fileUriString.isNotBlank() -> {
                try {
                    val uri = Uri.parse(fileUriString)
                    requireContext().contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    Toast.makeText(requireContext(), "Saved via URI", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            filePath.isNotBlank() && projectLoader != null -> {
                try {
                    projectLoader!!.updateFile(filePath, content)
                    Toast.makeText(requireContext(), "File updated in project", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Project save failed", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Toast.makeText(requireContext(), "No save location available", Toast.LENGTH_LONG).show()
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