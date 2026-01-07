// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/CodeEditorFragment.kt
// Author: CCVO
// Purpose: Displays and edits code chunks
// Copyright: CanC-code -CCVO

package com.llmassistant.ui

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.llmassistant.editor.ChunkManager
import com.llmassistant.editor.FileManager
import com.llmassistant.R
import java.io.File

class CodeEditorFragment : Fragment() {

    private lateinit var fileManager: FileManager
    private lateinit var chunkManager: ChunkManager

    private lateinit var scrollView: ScrollView
    private lateinit var codeTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileManager = FileManager(requireContext())
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
        chunkManager.loadFile(file)
        renderCurrentChunk(file)
    }

    private fun renderCurrentChunk(file: File) {
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

    fun nextChunk(file: File) {
        chunkManager.moveToNextChunk(file)
        renderCurrentChunk(file)
    }

    fun previousChunk(file: File) {
        chunkManager.moveToPreviousChunk(file)
        renderCurrentChunk(file)
    }
}