package io.canccode.aca

import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LLMFragment : Fragment(), LlamaBridge.GenerateCallback {

    private val TAG = "LLMFragment"

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button

    private val chatMessages = mutableListOf<Pair<String, String>>()
    private var lastPrompt: String = ""

    private var responseStartIndex: Int = 0
    private var currentResponse = StringBuilder()
    private var thinkingJob: Job? = null
    private var isThinking: Boolean = false

    private fun getSelectedModelFile(): File? {
        if (!isAdded) return null
        val prefs = requireContext().getSharedPreferences("model_prefs", android.content.Context.MODE_PRIVATE)
        val path = prefs.getString("model_path", null)
        return if (path != null) File(path) else null
    }

    private val modelDisplayName: String
        get() {
            val file = getSelectedModelFile()
            val fileName = file?.name ?: "Assistant"
            return when {
                fileName.contains("qwen", ignoreCase = true) -> "Qwen Coder"
                fileName.contains("mistral", ignoreCase = true) -> "Mistral"
                fileName.contains("llama", ignoreCase = true) -> "Llama 3"
                else -> "AI Assistant"
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatOutput = view.findViewById(R.id.chatOutput)
        chatScroll = view.findViewById(R.id.chatScroll)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)
        clearBtn = view.findViewById(R.id.clearBtn)

        updateStatusHeader("Ready")
        restoreChatHistory()

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener

            if (getSelectedModelFile() == null) {
                Toast.makeText(context, "Please select a model in Settings", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            inputBox.text.clear()
            lastPrompt = prompt
            generate(prompt)
        }

        clearBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Session")
                .setMessage("Clear all context history?")
                .setPositiveButton("Clear") { _, _ ->
                    LlamaBridge.shutdownNative() // Safest way to reset context
                    chatMessages.clear()
                    updateStatusHeader("Context Cleared")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateStatusHeader(status: String) {
        val header = "🤖 $modelDisplayName | $status\n\n"
        chatOutput.text = header
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.llm_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_model_rules -> { showModelRulesDialog(); true }
            R.id.action_history_settings -> { showHistorySettingsDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showModelRulesDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "e.g. You are a Senior Android Engineer..."
            minLines = 3
        }
        AlertDialog.Builder(requireContext())
            .setTitle("System Instructions")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                Toast.makeText(context, "System prompt updated", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showHistorySettingsDialog() {
        val options = arrayOf("Short (1 turn)", "Medium (3 turns)", "Long (10 turns)")
        AlertDialog.Builder(requireContext())
            .setTitle("Context Window")
            .setItems(options) { _, _ -> }.show()
    }

    private fun restoreChatHistory() {
        val sb = SpannableStringBuilder("🤖 $modelDisplayName Ready.\n\n")
        chatMessages.forEach { (user, assistant) ->
            sb.append("👤 You: $user\n\n")
            sb.append("🤖 $modelDisplayName: $assistant\n\n")
        }
        chatOutput.text = sb
        scrollToBottom()
    }

    private fun generate(prompt: String) {
        isThinking = true
        sendBtn.isEnabled = false
        inputBox.isEnabled = false

        chatOutput.append("👤 You: $prompt\n\n")
        chatOutput.append("🤖 $modelDisplayName: ")

        responseStartIndex = chatOutput.text.length
        currentResponse.setLength(0)

        startThinkingAnimation()
        scrollToBottom()

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Ensure model path is still valid
                val modelFile = getSelectedModelFile()
                if (modelFile == null || !modelFile.exists()) {
                    throw Exception("Model file missing")
                }

                LlamaBridge.generateNative(prompt, 1024, this@LLMFragment)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown Inference Error")
                }
            }
        }
    }

    override fun onToken(piece: String) {
        activity?.runOnUiThread {
            if (isThinking) {
                stopThinkingAnimation()
                isThinking = false
            }

            currentResponse.append(piece)
            
            // Efficiently update only the active response area
            val baseContent = chatOutput.text.subSequence(0, responseStartIndex)
            chatOutput.text = SpannableStringBuilder()
                .append(baseContent)
                .append(currentResponse)
            
            scrollToBottom()
        }
    }

    override fun onComplete(fullResponse: String) {
        activity?.runOnUiThread {
            isThinking = false
            stopThinkingAnimation()
            chatOutput.append("\n\n") 
            chatMessages.add(Pair(lastPrompt, fullResponse))
            resetInput()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            isThinking = false
            stopThinkingAnimation()
            chatOutput.append("\n\n❌ Error: $error\n\n")
            resetInput()
        }
    }

    private fun resetInput() {
        sendBtn.isEnabled = true
        inputBox.isEnabled = true
        inputBox.requestFocus()
        scrollToBottom()
    }

    private fun startThinkingAnimation() {
        thinkingJob?.cancel()
        thinkingJob = lifecycleScope.launch(Dispatchers.Main) {
            var dots = 0
            while (isThinking) {
                val dotStr = ".".repeat(dots + 1)
                val base = chatOutput.text.subSequence(0, responseStartIndex)
                chatOutput.text = SpannableStringBuilder().append(base).append("Thinking$dotStr")
                dots = (dots + 1) % 3
                delay(400)
            }
        }
    }

    private fun stopThinkingAnimation() {
        thinkingJob?.cancel()
        thinkingJob = null
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onDestroyView() {
        isThinking = false
        stopThinkingAnimation()
        super.onDestroyView()
    }
}
