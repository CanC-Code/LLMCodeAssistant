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

    private val conversationHistory = mutableListOf<Pair<String, String>>() // (user, assistant)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatOutput = view.findViewById(R.id.chatOutput)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)
        clearBtn = view.findViewById(R.id.clearBtn)

        chatOutput?.movementMethod = ScrollingMovementMethod()

        sendBtn?.setOnClickListener {
            val prompt = inputBox?.text?.toString()?.trim() ?: return@setOnClickListener
            if (prompt.isEmpty()) return@setOnClickListener

            inputBox?.text?.clear()
            generateAndDisplay(prompt)
        }

        clearBtn?.setOnClickListener {
            conversationHistory.clear()
            chatOutput?.text = "Chat cleared.\n\n"
            Toast.makeText(context, "Conversation reset", Toast.LENGTH_SHORT).show()
        }

        // Initial message
        val main = activity as? MainActivity
        chatOutput?.text = if (main?.isLLMReady() == true) {
            "✓ Model loaded and ready.\nAsk me anything!\n\n"
        } else {
            "⚠ No model loaded yet.\n\nGo to Settings → Pick a GGUF model file.\n\n"
        }
    }

    private fun generateAndDisplay(userPrompt: String) {
        val main = activity as? MainActivity ?: return

        if (!main.isLLMReady()) {
            chatOutput?.append("❌ Model not loaded. Go to Settings.\n\n")
            return
        }

        // Add user message to history (response placeholder)
        conversationHistory.add(Pair(userPrompt, ""))

        // Show user message immediately
        chatOutput?.append("👤 You: $userPrompt\n")
        chatOutput?.let { scrollToBottom(it) }

        // Disable input during generation
        sendBtn?.isEnabled = false
        inputBox?.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val formattedPrompt = buildMistralPrompt(userPrompt)
                Log.d(TAG, "Formatted prompt:\n$formattedPrompt")

                val rawResponse = LlamaBridge.generateNative(formattedPrompt, 768)

                val cleanResponse = cleanResponse(rawResponse)

                // Save assistant reply
                if (conversationHistory.isNotEmpty()) {
                    conversationHistory[conversationHistory.lastIndex] =
                        conversationHistory.last().first to cleanResponse
                }

                trimHistory()

                withContext(Dispatchers.Main) {
                    chatOutput?.append("🤖 Assistant: $cleanResponse\n\n")
                    chatOutput?.let { scrollToBottom(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                withContext(Dispatchers.Main) {
                    chatOutput?.append("❌ Error: ${e.message}\n\n")
                    chatOutput?.let { scrollToBottom(it) }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    sendBtn?.isEnabled = true
                    inputBox?.isEnabled = true
                }
            }
        }
    }

    private fun buildMistralPrompt(currentUserMessage: String): String {
        val sb = StringBuilder("<s>")  // BOS token - very important for Mistral

        if (conversationHistory.isEmpty()) {
            sb.append("[INST] $currentUserMessage [/INST]")
        } else {
            // All previous complete turns
            for (i in 0 until conversationHistory.size - 1) {
                val (user, assistant) = conversationHistory[i]
                sb.append("[INST] $user [/INST] $assistant</s>")
            }
            // Current (incomplete) turn
            sb.append("[INST] $currentUserMessage [/INST]")
        }

        return sb.toString()
    }

    private fun cleanResponse(raw: String): String {
        var text = raw.trim()
            .replace("</s>", "")
            .replace("[INST]", "")
            .replace("[/INST]", "")
            .replace("<s>", "")
            .trim()

        if (text.isBlank()) {
            text = "[No meaningful response generated]"
        }

        return text
    }

    private fun trimHistory(maxTurns: Int = 10) {
        while (conversationHistory.size > maxTurns) {
            conversationHistory.removeAt(0)
        }
    }

    private fun scrollToBottom(textView: TextView) {
        textView.post {
            val scrollAmount = textView.layout?.getLineTop(textView.lineCount) ?: 0
            textView.scrollTo(0, scrollAmount)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        conversationHistory.clear() // optional - prevents memory leak on config change
    }
}