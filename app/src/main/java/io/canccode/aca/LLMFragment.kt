package io.canccode.aca

import android.os.Bundle
import android.text.InputType
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

class LLMFragment : Fragment(), LlamaBridge.GenerateCallback {

    private val TAG = "LLMFragment"

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button

    private val chatMessages = mutableListOf<Pair<String, String>>()

    private var thinkingStart: Int = 0
    private var currentResponse = StringBuilder()
    private var thinkingJob: Job? = null

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

        restoreChatHistory()

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            inputBox.text.clear()
            generate(prompt)
        }

        clearBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Chat")
                .setMessage("Clear all chat history?")
                .setPositiveButton("Clear") { _, _ ->
                    LlamaBridge.clearHistoryNative()
                    chatMessages.clear()
                    chatOutput.text = "💬 Mistral LLM ready. Chat cleared.\n\n"
                    Toast.makeText(requireContext(), "Chat cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.llm_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

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
            hint = "Enter system rules for the model...\n\nExample:\nYou are a helpful coding assistant. Always provide complete, working code. Format code in markdown blocks."
            minLines = 5
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Model Rules (System Prompt)")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val rules = input.text.toString().trim()
                LlamaBridge.setModelRulesNative(rules.ifEmpty { null })
                Toast.makeText(requireContext(), "Model rules applied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHistorySettingsDialog() {
        val options = arrayOf("1 turn", "3 turns", "5 turns", "10 turns", "15 turns")
        val values = intArrayOf(1, 3, 5, 10, 15)

        AlertDialog.Builder(requireContext())
            .setTitle("Max Chat History")
            .setItems(options) { _, which ->
                LlamaBridge.setMaxHistoryTurnsNative(values[which])
                Toast.makeText(requireContext(), "History set to ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun restoreChatHistory() {
        if (chatMessages.isEmpty()) {
            chatOutput.text = "💬 Mistral LLM ready. Start chatting!\n\n"
        } else {
            val sb = StringBuilder("💬 Mistral LLM ready.\n\n")
            chatMessages.forEach { (user, assistant) ->
                sb.append("👤 You: $user\n\n")
                sb.append("🤖 Mistral: $assistant\n\n")
            }
            chatOutput.text = sb.toString()
        }
    }

    private fun generate(prompt: String) {
        sendBtn.isEnabled = false
        inputBox.isEnabled = false

        chatOutput.append("👤 You: $prompt\n\n")
        chatOutput.append("🤖 Mistral: ")
        thinkingStart = chatOutput.text.length
        currentResponse.clear()

        startThinkingAnimation()

        scrollToBottom()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                LlamaBridge.generateNative(prompt, 512, this@LLMFragment)
            } catch (e: Throwable) {
                Log.e(TAG, "Generation failed", e)
                withContext(Dispatchers.Main) {
                    stopThinkingAnimation()
                    chatOutput.append("❌ Error: ${e.message}\n\n")
                    sendBtn.isEnabled = true
                    inputBox.isEnabled = true
                    inputBox.requestFocus()
                    scrollToBottom()
                }
            }
        }
    }

    // Callback: called for each new token
    override fun onToken(piece: String) {
        activity?.runOnUiThread {
            stopThinkingAnimation()
            currentResponse.append(piece)
            chatOutput.text = chatOutput.text.substring(0, thinkingStart) + currentResponse.toString()
            scrollToBottom()
        }
    }

    // Callback: called when generation is fully done
    override fun onComplete(fullResponse: String) {
        activity?.runOnUiThread {
            stopThinkingAnimation()
            chatOutput.append("\n\n")  // extra line break for readability
            chatMessages.add(Pair(/* original prompt is lost here, but you can store it differently if needed */ "User", fullResponse))
            sendBtn.isEnabled = true
            inputBox.isEnabled = true
            inputBox.requestFocus()
            scrollToBottom()
        }
    }

    // Callback: called on error
    override fun onError(error: String) {
        activity?.runOnUiThread {
            stopThinkingAnimation()
            chatOutput.text = chatOutput.text.substring(0, thinkingStart)
            chatOutput.append("❌ $error\n\n")
            sendBtn.isEnabled = true
            inputBox.isEnabled = true
            inputBox.requestFocus()
            scrollToBottom()
        }
    }

    private fun startThinkingAnimation() {
        thinkingJob?.cancel()
        thinkingJob = lifecycleScope.launch(Dispatchers.Main) {
            var dots = ""
            while (true) {
                dots = when (dots) {
                    "" -> "."
                    "." -> ".."
                    ".." -> "..."
                    else -> ""
                }
                chatOutput.text = chatOutput.text.substring(0, thinkingStart) + "Thinking$dots"
                scrollToBottom()
                delay(500)
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