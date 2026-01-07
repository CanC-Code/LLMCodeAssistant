// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/CodeEditorFragment.kt
// Author: CCVO
// Purpose: Code editor fragment with chunked file handling, syntax highlighting, line numbers, wrap toggle, and LLM integration

package com.llmassistant.ui

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.llmassistant.editor.ChunkManager
import com.llmassistant.R
import java.io.File
import java.util.regex.Pattern

class CodeEditorFragment : Fragment() {

    private var filePath: String? = null
    private var projectRootPath: String? = null

    private lateinit var editorEditText: EditText
    private lateinit var lineNumbersView: TextView
    private lateinit var scrollView: HorizontalScrollView
    private lateinit var verticalScrollView: ScrollView
    private lateinit var wrapToggleButton: Button
    private lateinit var nextChunkButton: Button
    private lateinit var prevChunkButton: Button
    private lateinit var sendChunkButton: Button

    private var lineWrapEnabled = true
    private val chunkManager = ChunkManager()

    // -----------------------------
    // Chunking state
    // -----------------------------
    var currentChunkIndex = 0
        private set
    private val chunkSize = 400 // lines per chunk
    private var fullText: String = "" // store full file text for chunk navigation

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

        // Basic syntax highlighting
        private val KEYWORDS = arrayOf(
            "fun", "val", "var", "if", "else", "for", "while",
            "return", "class", "object", "interface", "package", "import"
        )
        private val KEYWORD_PATTERN = Pattern.compile("\\b(${KEYWORDS.joinToString("|")})\\b")
        private val STRING_PATTERN = Pattern.compile("\"(.*?)\"")
        private val COMMENT_PATTERN = Pattern.compile("//.*")
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
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }

        lineNumbersView = TextView(requireContext()).apply {
            setTextColor(0xFF888888.toInt())
            setPadding(8)
            gravity = Gravity.TOP or Gravity.END
        }

        editorEditText = EditText(requireContext()).apply {
            setTextColor(0xFF000000.toInt())
            setPadding(8)
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.TRANSPARENT)
            setHorizontallyScrolling(!lineWrapEnabled)
        }

        editorEditText.addTextChangedListener(object : TextWatcher {
            private var lastText: String = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val currentText = s.toString()
                if (currentText == lastText) return
                lastText = currentText
                fullText = currentText // update full text in case user edits
                highlightSyntax(currentText)
                updateLineNumbers(currentText)
            }
        })

        verticalScrollView = ScrollView(requireContext())
        verticalScrollView.addView(editorEditText)
        scrollView = HorizontalScrollView(requireContext())
        scrollView.addView(verticalScrollView)

        verticalScrollView.viewTreeObserver.addOnScrollChangedListener {
            lineNumbersView.scrollTo(0, verticalScrollView.scrollY)
        }

        layout.addView(lineNumbersView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        layout.addView(scrollView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)

        wrapToggleButton = Button(requireContext()).apply { text = "Toggle Wrap" }
        nextChunkButton = Button(requireContext()).apply { text = "Next Chunk" }
        prevChunkButton = Button(requireContext()).apply { text = "Prev Chunk" }
        sendChunkButton = Button(requireContext()).apply { text = "Send Chunk to LLM" }

        val buttonLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(prevChunkButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(nextChunkButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(wrapToggleButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sendChunkButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        }

        val containerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(layout, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(buttonLayout, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        setupButtonActions()
        loadFile()
        return containerLayout
    }

    private fun loadFile() {
        val file = filePath?.let { File(it) } ?: return
        if (!file.exists()) return
        fullText = chunkManager.loadFileChunks(file)
        resetChunks()
        showCurrentChunk()
    }

    private fun showCurrentChunk() {
        val chunkText = getChunk()
        editorEditText.setText(chunkText)
        highlightSyntax(chunkText)
        updateLineNumbers(chunkText)
    }

    private fun highlightSyntax(text: String) {
        val spannable = SpannableString(text)

        val matcherKeywords = KEYWORD_PATTERN.matcher(text)
        while (matcherKeywords.find()) {
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#0077CC")),
                matcherKeywords.start(), matcherKeywords.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val matcherStrings = STRING_PATTERN.matcher(text)
        while (matcherStrings.find()) {
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#AA5500")),
                matcherStrings.start(), matcherStrings.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val matcherComments = COMMENT_PATTERN.matcher(text)
        while (matcherComments.find()) {
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#888888")),
                matcherComments.start(), matcherComments.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val cursor = editorEditText.selectionStart
        editorEditText.setText(spannable)
        editorEditText.setSelection(cursor.coerceIn(0, spannable.length))
    }

    private fun updateLineNumbers(text: String) {
        val lines = text.split("\n")
        lineNumbersView.text = lines.indices.joinToString("\n") { (it + 1).toString() }
    }

    private fun setupButtonActions() {
        wrapToggleButton.setOnClickListener { toggleLineWrap() }
        nextChunkButton.setOnClickListener { nextChunk() }
        prevChunkButton.setOnClickListener { previousChunk() }
        sendChunkButton.setOnClickListener { sendCurrentChunkToLLM() }
    }

    fun toggleLineWrap() {
        lineWrapEnabled = !lineWrapEnabled
        editorEditText.setHorizontallyScrolling(!lineWrapEnabled)
        Toast.makeText(requireContext(), "Line wrap: $lineWrapEnabled", Toast.LENGTH_SHORT).show()
    }

    // -----------------------------
    // Chunk management
    // -----------------------------
    fun getChunk(index: Int = currentChunkIndex): String {
        val lines = fullText.lines()
        val start = index * chunkSize
        val end = minOf(start + chunkSize, lines.size)
        return if (start >= lines.size) "" else lines.subList(start, end).joinToString("\n")
    }

    fun nextChunk() {
        val totalChunks = (fullText.lines().size + chunkSize - 1) / chunkSize
        if (currentChunkIndex + 1 < totalChunks) {
            currentChunkIndex++
            showCurrentChunk()
            Toast.makeText(requireContext(), "Chunk ${currentChunkIndex + 1}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Already at last chunk", Toast.LENGTH_SHORT).show()
        }
    }

    fun previousChunk() {
        if (currentChunkIndex > 0) {
            currentChunkIndex--
            showCurrentChunk()
            Toast.makeText(requireContext(), "Chunk ${currentChunkIndex + 1}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Already at first chunk", Toast.LENGTH_SHORT).show()
        }
    }

    fun resetChunks() {
        currentChunkIndex = 0
    }

    // -----------------------------
    // LLM integration
    // -----------------------------
    private fun sendCurrentChunkToLLM() {
        val chunk = getChunk()
        if (chunk.isBlank()) {
            Toast.makeText(requireContext(), "No code in current chunk", Toast.LENGTH_SHORT).show()
            return
        }

        val mainActivity = activity as? MainActivity ?: return
        mainActivity.sendLLMInput(chunk) { response, chunkIndex ->
            val console = mainActivity.supportFragmentManager.findFragmentByTag("llm") as? OutputConsoleFragment
            val output = "[Chunk ${currentChunkIndex + 1}]\n$response"
            console?.appendOutput(output, OutputConsoleFragment.MessageType.LLM)
        }
    }

    fun getCurrentChunk(): String = getChunk()
}