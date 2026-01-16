// File: app/src/main/java/io/canccode/aca/LLMFragment.kt
package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LLMFragment : Fragment() {

    private lateinit var inputBox: EditText
    private lateinit var chatOutput: TextView
    private lateinit var sendBtn: Button
    private lateinit var llmHandler: LLMHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        llmHandler = LLMHandler(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_llm, container, false)

        inputBox = root.findViewById(R.id.inputBox)
        chatOutput = root.findViewById(R.id.chatOutput)
        sendBtn = root.findViewById(R.id.sendBtn)

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString()
            inputBox.setText("")

            lifecycleScope.launch {
                val response = withContext(Dispatchers.IO) {
                    llmHandler.infer(prompt)
                }
                chatOutput.append("\n> $prompt\n$response\n")
            }
        }

        return root
    }
}