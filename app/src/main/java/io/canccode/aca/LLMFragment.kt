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

    private val viewModel: AppViewModel by activityViewModels()

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var typingIndicator: TextView
    private lateinit var projectBadge: TextView

    private val streamBuffer = StringBuilder()
    private var streamStartLength = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_llm, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatOutput      = view.findViewById(R.id.chatOutput)
        chatScroll      = view.findViewById(R.id.chatScroll)
        inputBox        = view.findViewById(R.id.inputBox)
        sendBtn         = view.findViewById(R.id.sendBtn)
        clearBtn        = view.findViewById(R.id.clearBtn)
        typingIndicator = view.findViewById(R.id.typingIndicator)
        projectBadge    = view.findViewById(R.id.projectBadge)

        restoreHistory()
        setupObservers()

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener
            if (viewModel.isModelLoaded.value != true) {
                Toast.makeText(context, "Load a model first (Settings & Model)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            inputBox.text.clear()
            submitUserMessage(prompt)
        }

        clearBtn.setOnClickListener { confirmClear() }

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

    // ── History ───────────────────────────────────────────────────────────────

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
                AppViewModel.ChatMessage.Role.SYSTEM -> {}
            }
        }
        chatOutput.text = sb
        scrollToBottom()
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.isModelLoaded.observe(viewLifecycleOwner) { loaded ->
            sendBtn.isEnabled  = loaded
            inputBox.isEnabled = true
            inputBox.hint      = if (loaded) "Ask LLM…" else "Load a model first (Settings & Model)"
        }

        viewModel.llmInput.observe(viewLifecycleOwner) { input ->
            if (!input.isNullOrBlank()) inputBox.setText(input)
        }

        viewModel.projectName.observe(viewLifecycleOwner) { name ->
            if (name != null) {
                projectBadge.visibility = View.VISIBLE
                projectBadge.text       = "📁 $name"
                // Push a lightweight file-tree listing into the system rules so
                // the model knows what files exist — but NOT file contents yet.
                pushProjectSystemRules()
            } else {
                projectBadge.visibility = View.GONE
                LlamaBridge.setModelRules(null)
            }
        }
    }

    // ── Project-aware system rules ────────────────────────────────────────────

    /**
     * Sets a system prompt that tells the model the project file tree.
     * File CONTENTS are injected on-demand in submitUserMessage, only when
     * the user's question appears to reference a specific file.
     *
     * Keeping system rules to just the file listing stays well under the
     * context window limit on every turn.
     */
    private fun pushProjectSystemRules() {
        val files       = viewModel.projectContext.value ?: return
        val projectName = viewModel.projectName.value   ?: return
        if (files.isEmpty()) return

        val sb = StringBuilder()
        sb.appendLine("You are a coding assistant with knowledge of the following project: $projectName")
        sb.appendLine()
        sb.appendLine("Project file tree:")
        files.keys.sorted().forEach { sb.appendLine("  $it") }
        sb.appendLine()
        sb.appendLine("When asked about a specific file, I will provide its contents in the user message.")
        sb.appendLine("Answer questions about the project based on the context provided.")

        LlamaBridge.setModelRules(sb.toString())
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    private fun submitUserMessage(userText: String) {
        chatOutput.append("👤 You: $userText\n\n")
        scrollToBottom()

        viewModel.addChatMessage(
            AppViewModel.ChatMessage(AppViewModel.ChatMessage.Role.USER, userText)
        )

        // Build a prompt that ONLY injects the content of files the user
        // actually mentioned by name — not the entire project dump.
        val augmentedPrompt = buildAugmentedPrompt(userText)

        executeInference(augmentedPrompt)
    }

    /**
     * Looks for file paths mentioned in the user's message and appends only
     * those files' content — capped at MAX_INLINE_CHARS total.
     *
     * Example: "what does MainActivity.kt do?" → appends that file's content.
     * Example: "hello" → returns the raw user text with no augmentation.
     */
    private fun buildAugmentedPrompt(userText: String): String {
        val files = viewModel.projectContext.value
        if (files.isNullOrEmpty()) return userText

        val MAX_INLINE_CHARS = 3_000 // safe headroom within 4096 token context
        val matchedFiles = mutableListOf<Pair<String, String>>()
        var totalInline  = 0

        // Find any file whose name or path fragment appears in the user's text
        val lowerQuery = userText.lowercase()
        files.entries
            .sortedBy { it.key }
            .forEach { (path, content) ->
                val filename = path.substringAfterLast('/')
                if (lowerQuery.contains(filename.lowercase()) ||
                    lowerQuery.contains(path.lowercase())) {
                    if (totalInline + content.length <= MAX_INLINE_CHARS) {
                        matchedFiles.add(Pair(path, content))
                        totalInline += content.length
                    }
                }
            }

        if (matchedFiles.isEmpty()) return userText

        val sb = StringBuilder()
        sb.appendLine("Relevant file contents:")
        matchedFiles.forEach { (path, content) ->
            sb.appendLine("--- $path ---")
            sb.appendLine(content)
            sb.appendLine()
        }
        sb.appendLine("User question: $userText")
        return sb.toString()
    }

    private fun executeInference(prompt: String) {
        sendBtn.isEnabled  = false
        inputBox.isEnabled = false
        typingIndicator.visibility = View.VISIBLE

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
            chatOutput.append("\n\n")
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resetInputState() {
        typingIndicator.visibility = View.GONE
        sendBtn.isEnabled  = viewModel.isModelLoaded.value == true
        inputBox.isEnabled = true
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun confirmClear() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear conversation?")
            .setMessage("Clears chat display and resets the model's conversation memory.")
            .setPositiveButton("Clear") { _, _ ->
                LlamaBridge.clearHistory()
                viewModel.clearChatHistory()
                // Re-push system rules so the model still knows the project
                pushProjectSystemRules()
                chatOutput.text = "🤖 Conversation cleared. Ask anything.\n\n"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
