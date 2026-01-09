package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment

class LLMFragment : Fragment() {

    private lateinit var inputBox: EditText
    private lateinit var chatOutput: TextView
    private lateinit var sendBtn: Button
    private lateinit var projectLoader: ProjectLoader

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_llm, container, false)
        inputBox = root.findViewById(R.id.inputBox)
        chatOutput = root.findViewById(R.id.chatOutput)
        sendBtn = root.findViewById(R.id.sendBtn)
        return root
    }

    override fun onResume() {
        super.onResume()
        projectLoader = (activity as MainActivity).projectLoader

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString()
            val response = answerPrompt(prompt)
            chatOutput.append("\n> $prompt\n$response\n")
            inputBox.setText("")
        }
    }

    private fun answerPrompt(prompt: String): String {
        // Temporary: simple echo + file info
        val files = projectLoader.getAllFiles()
        return when {
            prompt.contains("list files", true) -> files.keys.joinToString("\n")
            prompt.contains("show file", true) -> {
                val name = prompt.substringAfterLast(" ").trim()
                files.entries.firstOrNull { it.key.contains(name) }?.value ?: "File not found."
            }
            else -> "LLM would respond here with context from loaded project."
        }
    }
}