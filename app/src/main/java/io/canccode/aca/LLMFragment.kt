package io.canccode.aca

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LLMFragment : Fragment(), LlamaBridge.GenerateCallback {

    private val TAG = "LLMFragment"

    private val viewModel: AppViewModel by activityViewModels()

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var typingIndicator: TextView
    private lateinit var projectBadge: TextView

    // Accumulates the streamed tokens for the current assistant turn
    private val streamBuffer = StringBuilder()

    // Marker appended to chatOutput so we can replace it as tokens arrive
    private var streamStartLength = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_llm, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatOutput       = view.findViewById(R.id.chatOutput)
        chatScroll       = view.findViewById(R.id.chatScroll)
        inputBox         = view.findViewById(R.id.inputBox)
        sendBtn          = view.findViewById(R.id.sendBtn)
        clearBtn         = view.findViewById(R.id.clearBtn)
        typingIndicator  = view.findViewById(R.id.typingIndicator)
        projectBadge     = view.findViewById(R.id.projectBadge)

        // Restore chat history from ViewModel (survives fragment transactions)
        restoreHistory()

        setupObservers()

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener
            if (viewModel.isModelLoaded.value != true) {
                Toast.makeText(context, "Load a model first via Settings & Model", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            inputBox.text.clear()
            submitUserMessage(prompt)
        }

        clearBtn.setOnClickListener { confirmClear() }

        // Long-press on chat output → copy full text
        chatOutput.setOnLongClickListener {
            val text = chatOutput.text.toString()
            if (text.isNotBlank()) {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Chat", text))
                Toast.makeText(context, "Chat copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    // ── History ────────────────────────────────────────────────────────────────

    private fun restoreHistory() {
        val history = viewModel.chatHistory.value ?: return
        if (history.isEmpty()) {
            chatOutput.text = "🤖 Assistant ready. Ask anything about your project or code.\n\n"
            return
        }
        val sb = SpannableStringBuilder()
        history.forEach { msg ->
            when (msg.role) {
                AppViewModel.ChatMessage.Role.USER ->
                    sb.append("👤 You: ${msg.text}\n\n")
                AppViewModel.ChatMessage.Role.ASSISTANT ->
                    sb.append("🤖 Assistant: ${msg.text}\n\n")
                AppViewModel.ChatMessage.Role.SYSTEM -> { /* not shown */ }
            }
        }
        chatOutput.text = sb
        scrollToBottom()
    }

    // ── Observers ──────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.isModelLoaded.observe(viewLifecycleOwner) { loaded ->
            sendBtn.isEnabled = loaded
            inputBox.isEnabled = true
            inputBox.hint = if (loaded) "Ask LLM..." else "Load a model first (Settings & Model)"
        }

        viewModel.llmInput.observe(viewLifecycleOwner) { input ->
            if (!input.isNullOrBlank()) inputBox.setText(input)
        }

        viewModel.projectName.observe(viewLifecycleOwner) { name ->
            if (name != null) {
                projectBadge.visibility = View.VISIBLE
                projectBadge.text = "📁 $name"
            } else {
                projectBadge.visibility = View.GONE
            }
        }
    }

    // ── Sending ────────────────────────────────────────────────────────────────

    private fun submitUserMessage(userText: String) {
        // Append to UI immediately
        chatOutput.append("👤 You: $userText\n\n")
        scrollToBottom()

        // Persist to ViewModel
        viewModel.addChatMessage(
            AppViewModel.ChatMessage(AppViewModel.ChatMessage.Role.USER, userText)
        )

        // Build the prompt with optional project context prepended
        val projectFiles = viewModel.projectContext.value ?: emptyMap()
        val projectName  = viewModel.projectName.value ?: ""
        val contextBlock = if (projectFiles.isNotEmpty())
            ProjectContextBuilder.formatForPrompt(projectName, projectFiles)
        else ""

        // Full prompt sent to the model: context block + user message
        // The native layer wraps this in ChatML, so we just provide the user turn here.
        // We prepend project context as extra user context in the message itself when present.
        val fullPrompt = if (contextBlock.isNotEmpty())
            "Use the following project context to answer accurately:\n\n$contextBlock\n\nUser question: $userText"
        else
            userText

        executeInference(fullPrompt)
    }

    private fun executeInference(prompt: String) {
        sendBtn.isEnabled = false
        inputBox.isEnabled = false
        typingIndicator.visibility = View.VISIBLE

        // Prepare stream marker
        streamBuffer.clear()
        chatOutput.append("🤖 Assistant: ")
        streamStartLength = chatOutput.text.length

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                LlamaBridge.generateNative(prompt, 1024, this@LLMFragment)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    chatOutput.append("[Error: ${e.message}]\n\n")
                    resetInputState()
                }
            }
        }
    }

    // ── LlamaBridge.GenerateCallback ──────────────────────────────────────────

    override fun onToken(piece: String) {
        streamBuffer.append(piece)
        lifecycleScope.launch(Dispatchers.Main) {
            // Replace everything after the "🤖 Assistant: " marker with the growing stream
            val current = chatOutput.text as? SpannableStringBuilder
                ?: SpannableStringBuilder(chatOutput.text)
            if (current.length > streamStartLength) {
                current.delete(streamStartLength, current.length)
            }
            current.append(streamBuffer)
            chatOutput.text = current
            scrollToBottom()
        }
    }

    override fun onComplete(fullResponse: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            // Final newline separation
            chatOutput.append("\n\n")

            // Persist complete assistant turn to ViewModel
            viewModel.addChatMessage(
                AppViewModel.ChatMessage(AppViewModel.ChatMessage.Role.ASSISTANT, fullResponse)
            )

            resetInputState()
            scrollToBottom()
        }
    }

    override fun onError(error: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            chatOutput.append("[Error: $error]\n\n")
            resetInputState()
            scrollToBottom()
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun resetInputState() {
        typingIndicator.visibility = View.GONE
        sendBtn.isEnabled = viewModel.isModelLoaded.value == true
        inputBox.isEnabled = true
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun confirmClear() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear conversation?")
            .setMessage("This will clear the chat display and reset the model's memory of this conversation.")
            .setPositiveButton("Clear") { _, _ ->
                LlamaBridge.clearHistory()
                viewModel.clearChatHistory()
                chatOutput.text = "🤖 Conversation cleared. Ask anything.\n\n"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
