package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Changed to activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LLMFragment : Fragment(), LlamaBridge.GenerateCallback {

    private val TAG = "LLMFragment"
    
    // USE activityViewModels to share state with the FileBrowser and Editor
    private val viewModel: AppViewModel by activityViewModels()

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

            // FIX: Check the SHARED ViewModel state before attempting inference
            if (viewModel.isModelLoaded.value != true) {
                Toast.makeText(context, "A model needs to be selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            executeInference(prompt)
            inputBox.text.clear()
        }

        clearBtn.setOnClickListener {
            LlamaBridge.clearHistoryNative()
            chatOutput.text = "🤖 Conversation history cleared.\n\n"
        }
    }

    private fun setupObservers() {
        // Observe changes to the prompt if sent from the Editor Fragment
        viewModel.llmInput.observe(viewLifecycleOwner) { input ->
            if (!input.isNullOrBlank()) {
                inputBox.setText(input)
            }
        }

        // Observe model loading status to update UI hint
        viewModel.isModelLoaded.observe(viewLifecycleOwner) { isLoaded ->
            if (!isLoaded) {
                chatOutput.append("\n⚠️ System: No model loaded. Please go to File Browser to pick a .gguf file.\n")
            }
        }
    }

    private fun executeInference(prompt: String) {
        // UI Feedback: Disable input
        sendBtn.isEnabled = false
        inputBox.isEnabled = false

        // Append user prompt visually
        chatOutput.append("\n👤 You: $prompt\n\n🤖 Assistant: ")
        scrollToBottom()

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Ensure the bridge is actually called
                LlamaBridge.generateNative(prompt, 1024, this@LLMFragment)
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Inference failed: ${e.message}", Toast.LENGTH_LONG).show()
                    sendBtn.isEnabled = true
                    inputBox.isEnabled = true
                }
            }
        }
    }

    // --- LlamaBridge.GenerateCallback Implementation ---

    override fun onToken(piece: String) {
        // Update UI on main thread as tokens stream in
        lifecycleScope.launch(Dispatchers.Main) {
            chatOutput.append(piece)
            scrollToBottom()
        }
    }

    override fun onComplete(fullResponse: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            chatOutput.append("\n\n")
            sendBtn.isEnabled = true
            inputBox.isEnabled = true
            scrollToBottom()
        }
    }

    override fun onError(error: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(context, "LLM Error: $error", Toast.LENGTH_SHORT).show()
            sendBtn.isEnabled = true
            inputBox.isEnabled = true
        }
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
