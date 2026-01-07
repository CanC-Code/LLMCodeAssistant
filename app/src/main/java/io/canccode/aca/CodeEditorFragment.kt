// File: LLMCodeAssistant/app/src/main/java/io/canccode/aca/CodeEditorFragment.kt
// Author: CCVO
// Purpose: Displays and edits code chunks
// Copyright: CanC-code - CCVO

package io.canccode.aca

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.File

class CodeEditorFragment : Fragment() {

    private lateinit var fileManager: FileManager
    private lateinit var chunkManager: ChunkManager

    private lateinit var scrollView: ScrollView
    private lateinit var codeTextView: TextView

    // Track current chunk index globally for MainActivity LLM integration
    var currentChunkIndex: Int = 0
        private set

    private var currentFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileManager = FileManager()
        chunkManager = ChunkManager(fileManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_code_editor, container, false)

        scrollView = view.findViewById(R.id.codeScrollView)
        codeTextView = TextView(requireContext()).apply {
            setTextAppearance(R.style.CodeEditorText)
        }

        scrollView.addView(
            codeTextView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

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
        val chunkText = chunkManager.getCurrentChunk(file)
        val spannable = SpannableString(chunkText)
        spannable.setSpan(
            null,
            0,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        codeTextView.text = spannable
    }

    fun nextChunk() {
        val file = currentFile ?: return
        chunkManager.moveToNextChunk(file)
        currentChunkIndex = chunkManager.getFileChunks(file)?.currentChunkIndex ?: 0
        renderCurrentChunk()
    }

    fun previousChunk() {
        val file = currentFile ?: return
        chunkManager.moveToPreviousChunk(file)
        currentChunkIndex = chunkManager.getFileChunks(file)?.currentChunkIndex ?: 0
        renderCurrentChunk()
    }

    /**
     * Returns the current chunk text for LLM input integration.
     */
    fun getCurrentChunk(): String {
        val file = currentFile ?: return ""
        return chunkManager.getCurrentChunk(file)
    }
}