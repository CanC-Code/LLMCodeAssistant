package io.canccode.aca

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
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

    private var chatOutput: TextView? = null
    private var inputBox: EditText? = null
    private var sendBtn: Button? = null
    private var clearBtn: Button? = null

    private val conversationHistory = mutableListOf<Pair<String, String>>() // user, assistant

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_llm, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating fragment", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            chatOutput = view.findViewById(R.id.chatOutput)
            inputBox = view.findViewById(R.id.inputBox)
            sendBtn = view.findViewById(R.id.sendBtn)
            clearBtn = view.findViewById(R.id.clearBtn)

            // Make TextView scrollable
            chatOutput?.movementMethod = ScrollingMovementMethod()

            sendBtn?.setOnClickListener {
                val prompt = inputBox?.text?.toString()?.trim()
                if (!prompt.isNullOrEmpty()) {
                    inputBox?.text?.clear()
                    generateAndDisplay(prompt)
                }
            }

            clearBtn?.setOnClickListener {
                conversationHistory.clear()
                chatOutput?.text = "Chat cleared.\n\n"
            }

            // Check if model is loaded
            val mainActivity = activity as? MainActivity
            if (mainActivity?.isLLMReady() == true) {
                chatOutput?.text = "✓ LLM Ready! Ask me anything.\n\n"
            } else {
                chatOutput?.text = "⚠ No model loaded.\n\nGo to Settings → Pick Existing GGUF File to select your model.\n\n"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            Toast.makeText(requireContext(), "View error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAndDisplay(userPrompt: String) {
        val mainActivity = activity as? MainActivity
        
        if (mainActivity?.isLLMReady() != true) {
            chatOutput?.append("❌ Model not loaded. Go to Settings.\n\n")
            return
        }

        // Add to conversation history
        conversationHistory.add(Pair(userPrompt, ""))

        // Display user message
        chatOutput?.append("👤 You: $userPrompt\n")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Format prompt with Mistral Instruct template
                val formattedPrompt = buildMistralPrompt(userPrompt)
                
                Log.d(TAG, "Sending prompt: $formattedPrompt")
                
                val response = LlamaBridge.generateNative(formattedPrompt, 512)
                
                // Clean response - remove template artifacts
                val cleanResponse = cleanResponse(response)
                
                // Update conversation history
                if (conversationHistory.isNotEmpty()) {
                    conversationHistory[conversationHistory.size - 1] = 
                        Pair(conversationHistory.last().first, cleanResponse)
                }

                withContext(Dispatchers.Main) {
                    chatOutput?.append("🤖 Assistant: $cleanResponse\n\n")
                    
                    // Auto-scroll to bottom
                    chatOutput?.let { textView ->
                        val scrollAmount = textView.layout?.getLineTop(textView.lineCount) ?: 0
                        textView.scrollTo(0, scrollAmount)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation error", e)
                withContext(Dispatchers.Main) {
                    chatOutput?.append("❌ Error: ${e.message}\n\n")
                }
            }
        }
    }

    private fun buildMistralPrompt(userMessage: String): String {
        // Mistral Instruct v0.2 format: <s>[INST] {prompt} [/INST]
        // For multi-turn: <s>[INST] {msg1} [/INST] {response1}</s> [INST] {msg2} [/INST]
        
        val builder = StringBuilder()
        
        if (conversationHistory.isEmpty()) {
            // First message
            builder.append("[INST] $userMessage [/INST]")
        } else {
            // Multi-turn conversation
            for (i in 0 until conversationHistory.size - 1) {
                val (user, assistant) = conversationHistory[i]
                if (i == 0) {
                    builder.append("[INST] $user [/INST] $assistant</s>")
                } else {
                    builder.append(" [INST] $user [/INST] $assistant</s>")
                }
            }
            // Current message (last in history, response not yet filled)
            builder.append(" [INST] $userMessage [/INST]")
        }
        
        return builder.toString()
    }

    private fun cleanResponse(response: String): String {
        // Remove common template artifacts
        var cleaned = response
            .replace("</s>", "")
            .replace("[INST]", "")
            .replace("[/INST]", "")
            .trim()
        
        // Remove leading/trailing whitespace and newlines
        cleaned = cleaned.trim()
        
        // If response is just unknown tokens, return helpful message
        if (cleaned.isEmpty() || cleaned.all { it == ' ' || it == '\n' }) {
            cleaned = "[Model generated empty response]"
        }
        
        return cleaned
    }
}