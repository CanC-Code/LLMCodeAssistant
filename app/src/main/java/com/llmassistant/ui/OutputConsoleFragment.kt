// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/OutputConsoleFragment.kt
// Author: CCVO
// Purpose: Shows LLM output and conversation log below editor, allows dynamic updates

package com.llmassistant.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.llmassistant.R

class OutputConsoleFragment : Fragment() {

    companion object {
        fun newInstance(): OutputConsoleFragment = OutputConsoleFragment()
    }

    private lateinit var scrollView: ScrollView
    private lateinit var consoleText: TextView
    private val buffer = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_output_console, container, false)
        scrollView = root.findViewById(R.id.console_scroll)
        consoleText = root.findViewById(R.id.console_text)
        return root
    }

    // -----------------------------
    // Append new LLM output
    // -----------------------------
    fun appendOutput(message: String) {
        buffer.append(message).append("\n\n")
        consoleText.text = buffer.toString()

        // Scroll to bottom
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    fun clearConsole() {
        buffer.clear()
        consoleText.text = ""
    }
}