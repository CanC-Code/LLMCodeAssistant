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
    private var isGenerating = false

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
                processUserInput(prompt)
            }
        }

        clearBtn.setOnClickListener {
            chatOutput.text = ""
            responseBuilder.clear()
        }
    }

    fun processUserInput(userInput: String) {
        if (isGenerating) {
            Toast.makeText(requireContext(), "Please wait for current response", Toast.LENGTH_SHORT).show()
            return
        }

        val activity = activity as? MainActivity
        if (activity?.isModelReady() != true) {
            Toast.makeText(requireContext(), "Model not loaded. Go to Settings.", Toast.LENGTH_SHORT).show()
            return
        }

        isGenerating = true
        sendBtn.isEnabled = false
        inputBox.isEnabled = false

        // Display user message
        chatOutput.append("👤 $userInput\n\n")
        
        // Prepare for response
        chatOutput.append("🤖 ")
        responseStart = chatOutput.text.length
        responseBuilder.clear()

        inputBox.text.clear()
        startThinking()

        // Generate response
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                LlamaBridge.generateNative(userInput, 512, this@LLMFragment)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    stopThinking()
                    chatOutput.append("❌ Error: ${e.message}\n\n")
                    resetUI()
                }
            }
        }
    }

    override fun onToken(piece: String) {
        activity?.runOnUiThread {
            stopThinking()
            responseBuilder.append(piece)
            
            // Update only the response part
            val prefix = chatOutput.text.substring(0, responseStart)
            chatOutput.text = prefix + responseBuilder.toString()
            
            scroll()
        }
    }

    override fun onComplete(fullResponse: String) {
        activity?.runOnUiThread {
            stopThinking()
            
            // Ensure final response is displayed
            val prefix = chatOutput.text.substring(0, responseStart)
            chatOutput.text = prefix + fullResponse + "\n\n"
            
            resetUI()
            scroll()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            stopThinking()
            
            // Remove "Thinking..." and show error
            chatOutput.text = chatOutput.text.substring(0, responseStart)
            chatOutput.append("❌ Error: $error\n\n")
            
            resetUI()
            scroll()
        }
    }

    private fun resetUI() {
        isGenerating = false
        sendBtn.isEnabled = true
        inputBox.isEnabled = true
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
                
                val prefix = chatOutput.text.substring(0, responseStart)
                chatOutput.text = prefix + "Thinking$dots"
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

    override fun onDestroyView() {
        stopThinking()
        super.onDestroyView()
    }
}