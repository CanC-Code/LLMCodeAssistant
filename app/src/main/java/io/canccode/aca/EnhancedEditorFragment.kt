package io.canccode.aca

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.text.style.BackgroundColorSpan
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Full-featured source code editor fragment.
 *
 * FIXES IN THIS REVISION
 * ──────────────────────
 * 1. HANG ON FILE OPEN (PRIMARY BUG)
 *    loadFileContent() called projectLoader.getFileContent(filePath) which is now a
 *    suspend function (doing IO on Dispatchers.IO). Calling it directly from
 *    onCreateView / onViewCreated on the main thread would crash with
 *    "Cannot access database on the main thread" equivalent — or, with the old
 *    synchronous version, it would block the main thread causing an ANR on SAF paths.
 *
 *    Fix: loadFileContent() is now a suspend fun launched in viewLifecycleOwner's
 *    lifecycleScope. It shows a loading indicator while reading and only populates
 *    the editor when the content is available.
 *
 * 2. LINE:COL NEVER UPDATED WHILE TYPING
 *    updateLineCol() was wired to onClick and onKeyListener but not to the
 *    TextWatcher. It was therefore always "Ln 1, Col 1" unless the user tapped
 *    the editor explicitly.
 *    Fix: call updateLineCol() inside afterTextChanged().
 *
 * 3. ACTION_SAVE_AS MENU ITEM UNHANDLED
 *    The editor menu declared action_save_as but onOptionsItemSelected had no branch
 *    for it, so tapping it would silently fall through to super.
 *    Fix: add a "Save As…" branch that launches a create-document picker.
 *
 * 4. UNDO STACK SEEDED WITH EMPTY STRING
 *    The undo stack was populated with "" before the async load completed, meaning
 *    the first undo after opening a file would wipe the whole content.
 *    Fix: seed the stack only after content is loaded.
 *
 * 5. SAVE BUTTON VISIBLE WHILE LOADING
 *    Minor UX: toolbar buttons were enabled immediately even before content was ready.
 *    Fix: disable toolbar during load and re-enable when done.
 */
class EnhancedEditorFragment : Fragment() {

    private val appViewModel: AppViewModel by activityViewModels()

    private lateinit var lineNumbersView:  TextView
    private lateinit var lineNumScroll:    android.widget.ScrollView
    private lateinit var editorView:       EditText
    private lateinit var editorScroll:     android.widget.ScrollView
    private lateinit var scrollContainer:  HorizontalScrollView
    private lateinit var lineColIndicator: TextView
    private lateinit var toolbarLayout:    LinearLayout
    private lateinit var loadingOverlay:   View

    private var filePath  = ""
    private var fileUri   = ""
    private lateinit var projectLoader: ProjectLoader

    // ── Undo / Redo ───────────────────────────────────────────────────────────
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var currentContent        = ""
    private var isUndoRedoOperation   = false
    private var isExternalUpdate      = false
    private var hasUnsavedChanges     = false
    private val MAX_UNDO              = 200

    // ── Save-As picker ────────────────────────────────────────────────────────
    private val saveAsPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val text = editorView.text.toString()
            try {
                requireContext().contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray())
                }
                hasUnsavedChanges = false
                updateTitle(false)
                Toast.makeText(requireContext(), "Saved as new file", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Save As failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
        fileUri  = arguments?.getString(ARG_URI,  "") ?: ""
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
        lineNumScroll    = root.findViewById(R.id.lineNumberScroll)
        editorView       = root.findViewById(R.id.editorText)
        editorScroll     = root.findViewById(R.id.editorInnerScroll)
        scrollContainer  = root.findViewById(R.id.editorScrollContainer)
        lineColIndicator = root.findViewById(R.id.lineColIndicator)
        toolbarLayout    = root.findViewById(R.id.editorToolbar)
        // A simple ProgressBar or semi-transparent overlay in the layout
        loadingOverlay   = root.findViewById(R.id.editorLoadingOverlay)

        setupEditor()
        setupToolbar(enabled = false)   // disabled until content is loaded
        setupViewModelObservers()

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadFileContentAsync()
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
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int)     {}
            override fun afterTextChanged(s: Editable?) {
                updateLineNumbers()
                // FIX: update cursor position on every keystroke, not just click
                updateLineCol()
                if (!isUndoRedoOperation && !isExternalUpdate) {
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

        editorView.setOnClickListener { updateLineCol() }
        editorView.setOnKeyListener   { _, _, _ -> updateLineCol(); false }

        // Sync line numbers with vertical scroll
        editorScroll.viewTreeObserver.addOnScrollChangedListener {
            lineNumScroll.scrollTo(0, editorScroll.scrollY)
        }
    }

    private fun setupToolbar(enabled: Boolean = true) {
        val btnUndo   = toolbarLayout.findViewById<ImageButton>(R.id.btnUndo)
        val btnRedo   = toolbarLayout.findViewById<ImageButton>(R.id.btnRedo)
        val btnSave   = toolbarLayout.findViewById<ImageButton>(R.id.btnSave)
        val btnAskLLM = toolbarLayout.findViewById<ImageButton>(R.id.btnAskLlm)

        listOf(btnUndo, btnRedo, btnSave, btnAskLLM).forEach { it.isEnabled = enabled }

        if (enabled) {
            btnUndo.setOnClickListener   { performUndo() }
            btnRedo.setOnClickListener   { performRedo() }
            btnSave.setOnClickListener   { saveFile() }
            btnAskLLM.setOnClickListener { askLlmAboutFile() }
        }
    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    private fun setupViewModelObservers() {
        appViewModel.editorContent.observe(viewLifecycleOwner) { newContent ->
            if (newContent == null) return@observe
            val currentText = editorView.text.toString()
            if (newContent == currentText) return@observe

            isExternalUpdate = true
            undoStack.addLast(currentText)
            if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            redoStack.clear()
            currentContent = newContent

            isUndoRedoOperation = true
            editorView.setText(newContent)
            isUndoRedoOperation = false
            isExternalUpdate = false

            updateLineNumbers()
            markUnsaved()
            flashGreen()
            updateTitle(true)
        }

        appViewModel.scrollToLine.observe(viewLifecycleOwner) { line ->
            if (line != null) {
                scrollEditorToLine(line)
                appViewModel.consumeScrollToLine()
            }
        }
    }

    // ── Load (ASYNC — fixes the hang) ─────────────────────────────────────────

    private fun loadFileContentAsync() {
        loadingOverlay.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                if (fileUri.isNotEmpty()) {
                    try {
                        val uri = Uri.parse(fileUri)
                        requireContext().contentResolver
                            .openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(),
                                "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        ""
                    }
                } else {
                    // FIX: getFileContent is now suspend — awaited properly
                    projectLoader.getFileContent(filePath)
                }
            }

            // Back on Main
            loadingOverlay.visibility = View.GONE
            setupToolbar(enabled = true)

            // FIX: seed undo stack AFTER content is ready, not before
            currentContent = content
            undoStack.clear()
            undoStack.addLast(content)

            isUndoRedoOperation = true
            editorView.setText(content)
            isUndoRedoOperation = false

            updateLineNumbers()
            updateTitle(false)

            val displayPath = filePath.ifEmpty { fileUri.substringAfterLast('/') }
            appViewModel.setActiveEditorFile(displayPath, content)
        }
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    private fun performUndo() {
        if (undoStack.size <= 1) {
            Toast.makeText(requireContext(), "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }
        isUndoRedoOperation = true
        redoStack.addLast(undoStack.removeLast())
        val restoredContent = undoStack.last()
        val changedLine = firstDifferentLine(currentContent, restoredContent)
        currentContent = restoredContent
        editorView.setText(restoredContent)
        editorView.setSelection(
            findLineStart(restoredContent, changedLine).coerceAtMost(editorView.length())
        )
        isUndoRedoOperation = false
        updateLineNumbers()
        scrollEditorToLine(changedLine)
        appViewModel.requestScrollToLine(changedLine)
        markUnsaved()
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show()
            return
        }
        isUndoRedoOperation = true
        val content = redoStack.removeLast()
        undoStack.addLast(content)
        val changedLine = firstDifferentLine(currentContent, content)
        currentContent = content
        editorView.setText(content)
        editorView.setSelection(
            findLineStart(content, changedLine).coerceAtMost(editorView.length())
        )
        isUndoRedoOperation = false
        updateLineNumbers()
        scrollEditorToLine(changedLine)
        appViewModel.requestScrollToLine(changedLine)
        markUnsaved()
    }

    private fun firstDifferentLine(a: String, b: String): Int {
        val aLines = a.lines(); val bLines = b.lines()
        val minLen = minOf(aLines.size, bLines.size)
        for (i in 0 until minLen) if (aLines[i] != bLines[i]) return i
        return minLen
    }

    private fun findLineStart(text: String, lineIndex: Int): Int {
        if (lineIndex == 0) return 0
        var count = 0
        for (i in text.indices) {
            if (text[i] == '\n') { count++; if (count == lineIndex) return i + 1 }
        }
        return text.length
    }

    private fun scrollEditorToLine(lineIndex: Int) {
        editorView.post {
            try {
                val layout = editorView.layout ?: return@post
                if (lineIndex >= layout.lineCount) return@post
                val y = layout.getLineTop(lineIndex)
                editorScroll.smoothScrollTo(0, (y - 80).coerceAtLeast(0))
                lineNumScroll.smoothScrollTo(0, (y - 80).coerceAtLeast(0))
            } catch (_: Exception) {}
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun saveFile() {
        val text = editorView.text.toString()
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = if (fileUri.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    try {
                        requireContext().contentResolver
                            .openOutputStream(Uri.parse(fileUri), "wt")?.use { it.write(text.toByteArray()) }
                        true
                    } catch (e: Exception) { false }
                }
            } else {
                // FIX: saveFile is now suspend
                projectLoader.saveFile(filePath, text)
            }

            if (ok) {
                hasUnsavedChanges = false
                updateTitle(false)
                val displayPath = filePath.ifEmpty { fileUri.substringAfterLast('/') }
                appViewModel.setActiveEditorFile(displayPath, text)
                Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Save failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Ask LLM ───────────────────────────────────────────────────────────────

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
        val col    = cursor - (text.lastIndexOf('\n').takeIf { it >= 0 } ?: -1)
        lineColIndicator.text = "Ln $line, Col $col"
    }

    private fun markUnsaved() {
        if (!hasUnsavedChanges) { hasUnsavedChanges = true; updateTitle(true) }
    }

    private fun updateTitle(unsaved: Boolean) {
        val name   = filePath.substringAfterLast('/').ifEmpty {
            fileUri.substringAfterLast('/').ifEmpty { "Untitled" }
        }
        val prefix = if (unsaved) "● " else ""
        (activity as? AppCompatActivity)?.supportActionBar?.title = "$prefix$name"
    }

    private fun flashGreen() {
        editorView.setBackgroundColor(Color.parseColor("#1A3D1A"))
        editorView.postDelayed({ editorView.setBackgroundColor(Color.parseColor("#1E1E1E")) }, 600)
    }

    // ── Options menu ──────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.editor_menu, menu)
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_save    -> { saveFile();    true }
        R.id.action_save_as -> {
            // FIX: handle Save As via create-document picker
            val defaultName = filePath.substringAfterLast('/').ifEmpty { "untitled.txt" }
            saveAsPicker.launch(defaultName)
            true
        }
        R.id.action_undo    -> { performUndo(); true }
        R.id.action_redo    -> { performRedo(); true }
        else                -> super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appViewModel.clearActiveEditorFile()
    }
}
