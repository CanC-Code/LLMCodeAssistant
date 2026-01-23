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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LLMFragment : Fragment() {

    private val TAG = "LLMFragment"

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button

    // Chat history stored in memory
    private val chatMessages = mutableListOf<Pair<String, String>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.hint = "Enter system rules for the model...\n\nExample:\nYou are a helpful coding assistant. Always provide complete, working code. Format code in markdown blocks."
        input.minLines = 5
        
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
        chatOutput.append("🤖 Mistral: Thinking...\n")
        val thinkingStart = chatOutput.text.length - "Thinking...\n".length

        scrollToBottom()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = LlamaBridge.generateNative(prompt, 512)

                withContext(Dispatchers.Main) {
                    val currentText = chatOutput.text.toString()
                    chatOutput.text = currentText.substring(0, thinkingStart)
                    
                    if (response.startsWith("[Error:")) {
                        chatOutput.append("❌ $response\n\n")
                    } else {
                        chatOutput.append("$response\n\n")
                        chatMessages.add(Pair(prompt, response))
                    }
                    
                    sendBtn.isEnabled = true
                    inputBox.isEnabled = true
                    inputBox.requestFocus()
                    
                    scrollToBottom()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Generation failed", e)
                withContext(Dispatchers.Main) {
                    val currentText = chatOutput.text.toString()
                    chatOutput.text = currentText.substring(0, thinkingStart)
                    
                    chatOutput.append("❌ Error: ${e.message}\n\n")
                    
                    sendBtn.isEnabled = true
                    inputBox.isEnabled = true
                    
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun scrollToBottom() {
        chatScroll.post {
            chatScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // MainActivity owns LLM lifecycle
    }
}