package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LLMFragment : Fragment(), LlamaBridge.GenerateCallback {

    private val TAG = "LLMFragment"
    private val viewModel: LLMViewModel by viewModels()

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        chatOutput = view.findViewById(R.id.chatOutput)
        chatScroll = view.findViewById(R.id.chatScroll)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)
        clearBtn = view.findViewById(R.id.clearBtn)

        setupObservers()

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener
            
            // Check if model is initialized in the bridge
            // In a production app, you'd track this state in a Repository
            executeInference(prompt)
            inputBox.text.clear()
        }

        clearBtn.setOnClickListener {
            LlamaBridge.clearHistoryNative()
            viewModel.clearOutput()
            chatOutput.text = "🤖 Conversation history cleared.\n\n"
        }
    }

    private fun setupObservers() {
        // Observe output for real-time streaming updates
        viewModel.output.observe(viewLifecycleOwner) { text ->
            chatOutput.text = text
            scrollToBottom()
        }

        // Handle button states
        viewModel.isGenerating.observe(viewLifecycleOwner) { isGenerating ->
            sendBtn.isEnabled = !isGenerating
            inputBox.isEnabled = !isGenerating
            if (isGenerating) {
                // Potential for a progress bar here
            }
        }

        // Handle errors
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, "Error: $it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeInference(prompt: String) {
        viewModel.setGenerating(true)
        
        // Append user prompt to the visual log
        val currentLog = viewModel.output.value ?: ""
        viewModel.setFullResponse("$currentLog\n👤 You: $prompt\n\n🤖 Assistant: ")

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                LlamaBridge.generateNative(prompt, 1024, this@LLMFragment)
            } catch (e: Exception) {
                viewModel.setError(e.message ?: "Inference failed")
            }
        }
    }

    // --- LlamaBridge.GenerateCallback Implementation ---

    override fun onToken(piece: String) {
        // Tokens arrive from C++ background thread
        viewModel.appendToken(piece)
    }

    override fun onComplete(fullResponse: String) {
        viewModel.setGenerating(false)
        viewModel.appendToken("\n\n")
    }

    override fun onError(error: String) {
        viewModel.setError(error)
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
