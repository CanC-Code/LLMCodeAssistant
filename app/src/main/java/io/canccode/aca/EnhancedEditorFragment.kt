package io.canccode.aca

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import java.util.*

/**
 * Full-featured source code editor fragment.
 *
 * Features:
 *  - Line numbers synced with editor scroll
 *  - Undo / Redo (stack-based, up to 200 states)
 *  - Save via SAF or ProjectLoader
 *  - Unsaved-changes indicator in the title
 *  - Line : Column indicator
 *  - "Ask LLM about this file" toolbar button
 *  - Notifies AppViewModel of the currently open file so LLMFragment
 *    always has context without the user having to ask
 */
class EnhancedEditorFragment : Fragment() {

    private val appViewModel: AppViewModel by activityViewModels()

    private lateinit var lineNumbersView: TextView
    private lateinit var editorView: EditText
    private lateinit var scrollContainer: HorizontalScrollView
    private lateinit var lineColIndicator: TextView
    private lateinit var toolbarLayout: LinearLayout

    private var filePath  = ""
    private var fileUri   = ""
    private lateinit var projectLoader: ProjectLoader

    // ── Undo / Redo ───────────────────────────────────────────────────────────
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var currentContent        = ""
    private var isUndoRedoOperation   = false
    private var hasUnsavedChanges     = false
    private val MAX_UNDO              = 200

    companion object {
        private const val ARG_PATH = "file_path"
        private const val ARG_URI  = "file_uri"

        fun newInstance(path: String, uri: String, loader: ProjectLoader) =
            EnhancedEditorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, path)
                    putString(ARG_URI, uri)
                }
                projectLoader = loader
            }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        filePath = arguments?.getString(ARG_PATH, "") ?: ""
        fileUri  = arguments?.getString(ARG_URI, "")  ?: ""
        if (!::projectLoader.isInitialized) {
            projectLoader = (activity as? MainActivity)?.getProjectLoader()
                ?: ProjectLoader(requireContext())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_enhanced_editor, container, false)

        lineNumbersView  = root.findViewById(R.id.lineNumbers)
        editorView       = root.findViewById(R.id.editorText)
        scrollContainer  = root.findViewById(R.id.editorScrollContainer)
        lineColIndicator = root.findViewById(R.id.lineColIndicator)
        toolbarLayout    = root.findViewById(R.id.editorToolbar)

        setupEditor()
        setupToolbar()
        loadFileContent()

        return root
    }

    // ── Editor setup ──────────────────────────────────────────────────────────

    private fun setupEditor() {
        editorView.setBackgroundColor(Color.parseColor("#1E1E1E"))
        editorView.setTextColor(Color.parseColor("#D4D4D4"))
        editorView.textSize = 14f
        editorView.setHorizontallyScrolling(true)
        editorView.typeface = android.graphics.Typeface.MONOSPACE

        lineNumbersView.setBackgroundColor(Color.parseColor("#252525"))
        lineNumbersView.setTextColor(Color.parseColor("#858585"))
        lineNumbersView.textSize = 14f
        lineNumbersView.setPadding(16, 0, 16, 0)
        lineNumbersView.typeface = android.graphics.Typeface.MONOSPACE

        editorView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLineNumbers()
                if (!isUndoRedoOperation) {
                    val new = s.toString()
                    if (new != currentContent) {
                        undoStack.addLast(currentContent)
                        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
                        redoStack.clear()
                        currentContent = new
                        markUnsaved()
                    }
                }
            }
        })

        // Line : Col indicator
        editorView.setOnClickListener { updateLineCol() }
        editorView.setOnKeyListener { _, _, _ -> updateLineCol(); false }
    }

    private fun setupToolbar() {
        val btnUndo    = toolbarLayout.findViewById<ImageButton>(R.id.btnUndo)
        val btnRedo    = toolbarLayout.findViewById<ImageButton>(R.id.btnRedo)
        val btnSave    = toolbarLayout.findViewById<ImageButton>(R.id.btnSave)
        val btnAskLLM  = toolbarLayout.findViewById<ImageButton>(R.id.btnAskLlm)

        btnUndo.setOnClickListener   { performUndo() }
        btnRedo.setOnClickListener   { performRedo() }
        btnSave.setOnClickListener   { saveFile() }
        btnAskLLM.setOnClickListener { askLlmAboutFile() }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private fun loadFileContent() {
        val content = if (fileUri.isNotEmpty()) {
            try {
                val uri = Uri.parse(fileUri)
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                } ?: ""
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
                ""
            }
        } else {
            projectLoader.getFileContent(filePath)
        }

        currentContent = content
        undoStack.clear()
        undoStack.addLast(content)
        isUndoRedoOperation = true
        editorView.setText(content)
        isUndoRedoOperation = false
        updateLineNumbers()
        updateTitle(false)

        // Notify the ViewModel so LLMFragment always knows what file is open
        val displayPath = filePath.ifEmpty { fileUri.substringAfterLast('/') }
        appViewModel.setActiveEditorFile(displayPath, content)
    }

    // ── Toolbar actions ───────────────────────────────────────────────────────

    private fun saveFile() {
        val text = editorView.text.toString()
        val ok = if (fileUri.isNotEmpty()) {
            try {
                val uri = Uri.parse(fileUri)
                requireContext().contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray())
                }
                true
            } catch (e: Exception) { false }
        } else {
            projectLoader.saveFile(filePath, text)
        }

        if (ok) {
            hasUnsavedChanges = false
            updateTitle(false)
            // Update the ViewModel's active file content after save
            val displayPath = filePath.ifEmpty { fileUri.substringAfterLast('/') }
            appViewModel.setActiveEditorFile(displayPath, text)
            Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Save failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun performUndo() {
        if (undoStack.size > 1) {
            isUndoRedoOperation = true
            redoStack.addLast(undoStack.removeLast())
            currentContent = undoStack.last()
            editorView.setText(currentContent)
            editorView.setSelection(currentContent.length.coerceAtMost(editorView.length()))
            isUndoRedoOperation = false
            updateLineNumbers()
        } else {
            Toast.makeText(requireContext(), "Nothing to undo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRedo() {
        if (redoStack.isNotEmpty()) {
            isUndoRedoOperation = true
            val content = redoStack.removeLast()
            undoStack.addLast(content)
            currentContent = content
            editorView.setText(content)
            editorView.setSelection(currentContent.length.coerceAtMost(editorView.length()))
            isUndoRedoOperation = false
            updateLineNumbers()
        } else {
            Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens a dialog pre-filled with "Explain this file: <filename>" and
     * navigates to LLMFragment, passing the full file content as context.
     *
     * Because AppViewModel already holds activeFileContent from setActiveEditorFile(),
     * the LLMFragment will automatically have the file content in its system rules.
     */
    private fun askLlmAboutFile() {
        val fileName = filePath.substringAfterLast('/')
            .ifEmpty { fileUri.substringAfterLast('/') }

        val input = EditText(requireContext()).apply {
            setText("Explain what $fileName does and how it works.")
            setSelection(length())
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Ask LLM about $fileName")
            .setView(input)
            .setPositiveButton("Ask") { _, _ ->
                val question = input.text.toString().trim()
                if (question.isNotEmpty()) {
                    appViewModel.sendToLLM(question)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, LLMFragment())
                        .addToBackStack(null)
                        .commit()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun updateLineNumbers() {
        val lines = editorView.text.toString().split("\n")
        lineNumbersView.text = (1..lines.size).joinToString("\n")
    }

    private fun updateLineCol() {
        val cursor = editorView.selectionStart.coerceAtLeast(0)
        val text   = editorView.text.toString().take(cursor)
        val line   = text.count { it == '\n' } + 1
        val col    = cursor - text.lastIndexOf('\n')
        lineColIndicator.text = "Ln $line, Col $col"
    }

    private fun markUnsaved() {
        if (!hasUnsavedChanges) {
            hasUnsavedChanges = true
            updateTitle(true)
        }
    }

    private fun updateTitle(unsaved: Boolean) {
        val name = filePath.substringAfterLast('/')
            .ifEmpty { fileUri.substringAfterLast('/').ifEmpty { "Untitled" } }
        val prefix = if (unsaved) "● " else ""
        (activity as? AppCompatActivity)?.supportActionBar?.title = "$prefix$name"
    }

    // ── Options menu (fallback for devices without toolbar) ───────────────────

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.editor_menu, menu)
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_save  -> { saveFile();    true }
        R.id.action_undo  -> { performUndo(); true }
        R.id.action_redo  -> { performRedo(); true }
        else              -> super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear active file context when the editor closes
        appViewModel.clearActiveEditorFile()
    }
}
