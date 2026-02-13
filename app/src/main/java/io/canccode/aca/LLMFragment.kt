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

    private var thinkingStart: Int = 0
    private var currentResponse = StringBuilder()
    private var thinkingJob: Job? = null

    /**
     * Helper to get the model file. 
     * Based on your previous logs, we check the shared preferences 
     * used in SettingsFragment.
     */
    private fun getSelectedModelFile(): File? {
        val prefs = requireContext().getSharedPreferences("model_prefs", android.content.Context.MODE_PRIVATE)
        val path = prefs.getString("model_path", null)
        return if (path != null) File(path) else null
    }

    private val modelDisplayName: String
        get() {
            val file = getSelectedModelFile()
            val fileName = file?.name ?: "LLM"
            return when {
                fileName.contains("qwen", ignoreCase = true) -> "Qwen2.5-Coder"
                fileName.contains("mistral", ignoreCase = true) -> "Mistral"
                fileName.contains("llama", ignoreCase = true) -> "Llama-3"
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
            if (prompt.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if model is loaded before sending
            if (getSelectedModelFile() == null) {
                Toast.makeText(requireContext(), "Please select a model in Settings first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            inputBox.text.clear()
            lastPrompt = prompt
            generate(prompt)
        }

        clearBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Chat")
                .setMessage("Clear all chat history from memory?")
                .setPositiveButton("Clear") { _, _ ->
                    LlamaBridge.clearHistoryNative()
                    chatMessages.clear()
                    chatOutput.text = "🤖 $modelDisplayName | Chat cleared\n\n"
                    Toast.makeText(requireContext(), "Memory cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateStatusHeader(status: String) {
        val header = "🤖 $modelDisplayName | $status\n\n"
        chatOutput.text = header
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.llm_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_model_rules -> {
                showModelRulesDialog()
                true
            }
            R.id.action_history_settings -> {
                showHistorySettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showModelRulesDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Example: You are an expert Android developer..."
            minLines = 5
        }

        AlertDialog.Builder(requireContext())
            .setTitle("$modelDisplayName Rules")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val rules = input.text.toString().trim()
                LlamaBridge.setModelRulesNative(rules.ifEmpty { "" })
                Toast.makeText(requireContext(), "Rules applied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHistorySettingsDialog() {
        val options = arrayOf("1 turn", "3 turns", "5 turns", "8 turns")
        val values = intArrayOf(1, 3, 5, 8)

        AlertDialog.Builder(requireContext())
            .setTitle("Context Window (History)")
            .setItems(options) { _, which ->
                LlamaBridge.setMaxHistoryTurnsNative(values[which])
                Toast.makeText(requireContext(), "History set to ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
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
        sendBtn.isEnabled = false
        inputBox.isEnabled = false

        chatOutput.append("👤 You: $prompt\n\n")
        chatOutput.append("🤖 $modelDisplayName: ")

        thinkingStart = chatOutput.text.length
        currentResponse.setLength(0)

        startThinkingAnimation()
        scrollToBottom()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                LlamaBridge.generateNative(prompt, 1024, this@LLMFragment)
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                withContext(Dispatchers.Main) {
                    stopThinkingAnimation()
                    chatOutput.append("❌ Critical Error: ${e.message}\n\n")
                    resetInput()
                }
            }
        }
    }

    override fun onToken(piece: String) {
        activity?.runOnUiThread {
            if (thinkingJob != null) stopThinkingAnimation()

            currentResponse.append(piece)
            
            // Fixed the unresolved SpannableStringBuilder error by importing it
            val builder = SpannableStringBuilder()
                .append(chatOutput.text.subSequence(0, thinkingStart))
                .append(currentResponse.toString())
            
            chatOutput.text = builder
            scrollToBottom()
        }
    }

    override fun onComplete(fullResponse: String) {
        activity?.runOnUiThread {
            stopThinkingAnimation()
            chatOutput.append("\n\n") 
            chatMessages.add(Pair(lastPrompt, fullResponse))
            resetInput()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            stopThinkingAnimation()
            val baseText = chatOutput.text.subSequence(0, thinkingStart)
            chatOutput.text = SpannableStringBuilder().append(baseText).append("❌ $error\n\n")
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
            while (true) {
                val dotStr = ".".repeat(dots + 1)
                val builder = SpannableStringBuilder()
                    .append(chatOutput.text.subSequence(0, thinkingStart))
                    .append("Thinking$dotStr")
                chatOutput.text = builder
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
        super.onDestroyView()
        stopThinkingAnimation()
    }
}
