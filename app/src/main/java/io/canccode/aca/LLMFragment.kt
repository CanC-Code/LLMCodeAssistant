package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LLMFragment : Fragment() {

    private val TAG = "LLMFragment"

    private lateinit var chatOutput: TextView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chatOutput = view.findViewById(R.id.chatOutput)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)
        clearBtn = view.findViewById(R.id.clearBtn)

        chatOutput.text = "💬 Mistral LLM ready. Start chatting!\n\n"

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            inputBox.text.clear()
            generate(prompt)
        }

        clearBtn.setOnClickListener {
            LlamaBridge.clearHistoryNative()
            chatOutput.text = "💬 Mistral LLM ready. Chat history cleared.\n\n"
            Toast.makeText(requireContext(), "Chat history cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generate(prompt: String) {
        // Disable input during generation
        sendBtn.isEnabled = false
        inputBox.isEnabled = false
        
        // Show user message
        chatOutput.append("👤 You: $prompt\n\n")
        
        // Show thinking indicator
        chatOutput.append("🤖 Mistral: Thinking...\n")
        val thinkingStart = chatOutput.text.length - "Thinking...\n".length

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = LlamaBridge.generateNative(prompt, 512)

                withContext(Dispatchers.Main) {
                    // Remove "Thinking..." text
                    val currentText = chatOutput.text.toString()
                    chatOutput.text = currentText.substring(0, thinkingStart)
                    
                    // Add actual response
                    if (response.startsWith("[Error:")) {
                        chatOutput.append("❌ $response\n\n")
                    } else {
                        chatOutput.append("$response\n\n")
                    }
                    
                    // Re-enable input
                    sendBtn.isEnabled = true
                    inputBox.isEnabled = true
                    inputBox.requestFocus()
                    
                    // Scroll to bottom
                    val scrollView = view?.findViewById<View>(R.id.chatScroll) as? android.widget.ScrollView
                    scrollView?.post {
                        scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Generation failed", e)
                withContext(Dispatchers.Main) {
                    // Remove "Thinking..." text
                    val currentText = chatOutput.text.toString()
                    chatOutput.text = currentText.substring(0, thinkingStart)
                    
                    chatOutput.append("❌ Error: ${e.message}\n\n")
                    
                    sendBtn.isEnabled = true
                    inputBox.isEnabled = true
                    
                    Toast.makeText(
                        requireContext(),
                        "LLM error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // DO NOT shutdown here — MainActivity owns lifecycle
    }
}