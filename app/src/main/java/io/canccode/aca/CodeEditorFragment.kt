// File: LLMCodeAssistant/app/src/main/java/io/canccode/aca/CodeEditorFragment.kt
// Author: CCVO
// Purpose: Displays and edits code chunks

package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.File

// ---- Custom classes ----
import com.llmassistant.editor.FileManager
import com.llmassistant.editor.ChunkManager

class CodeEditorFragment : Fragment() {

    companion object {
        private const val ARG_FILE_PATH = "file_path"

        fun newInstance(filePath: String): CodeEditorFragment =
            CodeEditorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PATH, filePath)
                }
            }
    }

    private lateinit var fileManager: FileManager
    private lateinit var chunkManager: ChunkManager

    private lateinit var scrollView: ScrollView
    private lateinit var codeTextView: TextView

    var currentChunkIndex: Int = 0
        private set

    private var currentFile: File? = null
    private var lineWrapEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileManager = FileManager()
        chunkManager = ChunkManager(fileManager)

        arguments?.getString(ARG_FILE_PATH)?.let {
            currentFile = File(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_code_editor, container, false)

        scrollView = view.findViewById(R.id.codeScrollView)
        codeTextView = view.findViewById(R.id.codeTextView)

        codeTextView.isHorizontallyScrolling = !lineWrapEnabled

        currentFile?.let { loadFile(it) }

        return view
    }

    fun loadFile(file: File) {
        currentFile = file
        chunkManager.loadFile(file)
        currentChunkIndex = 0
        renderCurrentChunk()
    }

    private fun renderCurrentChunk() {
        val file = currentFile ?: return
        codeTextView.text = chunkManager.getCurrentChunk(file)
    }

    fun nextChunk() {
        val file = currentFile ?: return
        chunkManager.moveToNextChunk(file)
        currentChunkIndex = chunkManager.currentChunkIndex(file)
        renderCurrentChunk()
    }

    fun previousChunk() {
        val file = currentFile ?: return
        chunkManager.moveToPreviousChunk(file)
        currentChunkIndex = chunkManager.currentChunkIndex(file)
        renderCurrentChunk()
    }

    fun getCurrentChunk(): String {
        val file = currentFile ?: return ""
        return chunkManager.getCurrentChunk(file)
    }

    fun toggleLineWrap() {
        lineWrapEnabled = !lineWrapEnabled
        codeTextView.isHorizontallyScrolling = !lineWrapEnabled
    }
}