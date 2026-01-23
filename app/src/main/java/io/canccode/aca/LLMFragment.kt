package io.canccode.aca

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class LLMFragment : Fragment(), LlamaBridge.GenerateCallback {

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button

    private val responseBuilder = StringBuilder()
    private var thinkingJob: Job? = null
    private var responseStart = 0
    
    // Chat history
    private val chatHistory = mutableListOf<ChatMessage>()

    data class ChatMessage(
        val isUser: Boolean,
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chatOutput = view.findViewById(R.id.chatOutput)
        chatScroll = view.findViewById(R.id.chatScroll)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)
        clearBtn = view.findViewById(R.id.clearBtn)

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isNotEmpty()) {
                addUserMessage(prompt)
                inputBox.text.clear()
            }
        }

        clearBtn.setOnClickListener {
            clearChat()
        }

        // Restore chat history if any
        restoreChatHistory()
    }

    fun addUserMessage(prompt: String) {
        // Add to history
        chatHistory.add(ChatMessage(isUser = true, text = prompt))
        
        // Display
        chatOutput.append("👤 You: $prompt\n\n")
        scroll()
        
        // Generate response
        generate(prompt)
    }

    private fun generate(prompt: String) {
        sendBtn.isEnabled = false

        chatOutput.append("🤖 Assistant: ")
        responseStart = chatOutput.text.length
        responseBuilder.clear()

        startThinking()

        lifecycleScope.launch(Dispatchers.IO) {
            LlamaBridge.generateNative(prompt, 512, this@LLMFragment)
        }
    }

    override fun onToken(piece: String) {
        activity?.runOnUiThread {
            stopThinking()
            responseBuilder.append(piece)
            chatOutput.text =
                chatOutput.text.substring(0, responseStart) + responseBuilder.toString()
            scroll()
        }
    }

    override fun onComplete(fullResponse: String) {
        activity?.runOnUiThread {
            stopThinking()
            
            // Add to history
            chatHistory.add(ChatMessage(isUser = false, text = fullResponse))
            
            chatOutput.append("\n\n")
            sendBtn.isEnabled = true
            scroll()
            
            saveChatHistory()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            stopThinking()
            
            val errorMsg = "Error: $error"
            chatHistory.add(ChatMessage(isUser = false, text = errorMsg))
            
            chatOutput.append("❌ $error\n\n")
            sendBtn.isEnabled = true
            scroll()
            
            saveChatHistory()
        }
    }

    private fun startThinking() {
        thinkingJob?.cancel()
        thinkingJob = lifecycleScope.launch {
            var dots = ""
            while (true) {
                dots = when (dots) {
                    "" -> "."
                    "." -> ".."
                    ".." -> "..."
                    else -> ""
                }
                chatOutput.text =
                    chatOutput.text.substring(0, responseStart) + "Thinking$dots"
                scroll()
                delay(400)
            }
        }
    }

    private fun stopThinking() {
        thinkingJob?.cancel()
        thinkingJob = null
    }

    private fun scroll() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun clearChat() {
        chatHistory.clear()
        chatOutput.text = ""
        saveChatHistory()
        Toast.makeText(requireContext(), "Chat cleared", Toast.LENGTH_SHORT).show()
    }

    private fun saveChatHistory() {
        val prefs = requireContext().getSharedPreferences("llm_prefs", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save as JSON-like string
        val historyStr = chatHistory.joinToString("|||") { msg ->
            "${if (msg.isUser) "U" else "A"}::${msg.text}"
        }
        
        editor.putString("chat_history", historyStr)
        editor.apply()
    }

    private fun restoreChatHistory() {
        val prefs = requireContext().getSharedPreferences("llm_prefs", android.content.Context.MODE_PRIVATE)
        val historyStr = prefs.getString("chat_history", "") ?: ""
        
        if (historyStr.isNotEmpty()) {
            chatHistory.clear()
            
            historyStr.split("|||").forEach { entry ->
                if (entry.contains("::")) {
                    val parts = entry.split("::", limit = 2)
                    val isUser = parts[0] == "U"
                    val text = parts[1]
                    
                    chatHistory.add(ChatMessage(isUser, text))
                    
                    if (isUser) {
                        chatOutput.append("👤 You: $text\n\n")
                    } else {
                        chatOutput.append("🤖 Assistant: $text\n\n")
                    }
                }
            }
            
            scroll()
        }
    }

    override fun onDestroyView() {
        stopThinking()
        super.onDestroyView()
    }
}