package io.canccode.aca

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class LLMFragment : Fragment() {

    private val TAG = "LLMFragment"

    private val viewModel: AppViewModel by activityViewModels()

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var typingIndicator: TextView
    private lateinit var projectBadge: TextView

    private val streamBuffer   = StringBuilder()
    private var streamStartLen = 0

    // ── LlmService binding ────────────────────────────────────────────────────

    private var llmService: LlmService? = null
    private var serviceBound  = false

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            llmService  = (binder as LlmService.LocalBinder).getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            llmService  = null
        }
    }

    // ── View ──────────────────────────────────────────────────────────────────

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

        // Bind to the foreground service (already started by MainActivity)
        requireContext().bindService(
            Intent(requireContext(), LlmService::class.java),
            serviceConn,
            Context.BIND_AUTO_CREATE
        )

        restoreHistory()
        setupObservers()

        sendBtn.setOnClickListener {
            val text = inputBox.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            if (viewModel.isModelLoaded.value != true) {
                Toast.makeText(context, "Load a model first (Settings & Model)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            inputBox.text.clear()
            submitUserMessage(text)
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

    override fun onStart() {
        super.onStart()
        if (!serviceBound) {
            requireContext().bindService(
                Intent(requireContext(), LlmService::class.java),
                serviceConn,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            requireContext().unbindService(serviceConn)
            serviceBound = false
        }
    }

    // ── History restoration ───────────────────────────────────────────────────

    private fun restoreHistory() {
        val history = viewModel.chatHistory.value ?: emptyList()
        if (history.isEmpty()) {
            chatOutput.text = "🤖 Assistant ready. Ask anything about your project or code.\n\n"
            return
        }
        val sb = SpannableStringBuilder()
        history.forEach { msg ->
            when (msg.role) {
                AppViewModel.ChatMessage.Role.USER      -> sb.append("👤 You: ${msg.text}\n\n")
                AppViewModel.ChatMessage.Role.ASSISTANT -> sb.append("🤖 Assistant: ${msg.text}\n\n")
            }
        }
        chatOutput.text = sb
        scrollToBottom()
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.isModelLoaded.observe(viewLifecycleOwner) { loaded ->
            sendBtn.isEnabled  = loaded && !isGenerating()
            inputBox.isEnabled = true
            inputBox.hint      = if (loaded) "Ask LLM…" else "Load a model first (Settings & Model)"
        }

        // Global bar sends a prompt here via ViewModel relay
        viewModel.llmInput.observe(viewLifecycleOwner) { input ->
            if (!input.isNullOrBlank()) {
                viewModel.consumeLlmInput()
                inputBox.setText(input)
                // Auto-submit
                val text = input.trim()
                if (text.isNotEmpty() && viewModel.isModelLoaded.value == true) {
                    inputBox.text.clear()
                    submitUserMessage(text)
                }
            }
        }

        viewModel.projectName.observe(viewLifecycleOwner) { name ->
            if (name != null) {
                projectBadge.visibility = View.VISIBLE
                projectBadge.text       = "📁 $name"
                updateSystemRules()
            } else {
                projectBadge.visibility = View.GONE
                LlamaBridge.setModelRules(null)
            }
        }

        viewModel.activeFilePath.observe(viewLifecycleOwner) { path ->
            // Update the badge to show the active editor file
            if (path != null) {
                val fileName = path.substringAfterLast('/')
                projectBadge.visibility = View.VISIBLE
                val projectPart = viewModel.projectName.value?.let { "📁 $it" } ?: ""
                projectBadge.text = "$projectPart  ✏️ $fileName"
            }
            // Always rebuild system rules when the active file changes
            updateSystemRules()
        }
    }

    // ── System rules ──────────────────────────────────────────────────────────

    /**
     * Builds the system prompt from:
     *   1. Project file tree listing (lightweight — just file names)
     *   2. Currently open file's full content
     *
     * This is called whenever the project or active file changes.
     * It does NOT include all project file contents — those are injected
     * on-demand in buildAugmentedPrompt() when the user asks about a specific file.
     */
    private fun updateSystemRules() {
        val sb = StringBuilder()

        val projectName = viewModel.projectName.value
        val files       = viewModel.projectContext.value ?: emptyMap()

        if (projectName != null && files.isNotEmpty()) {
            sb.appendLine("You are an expert coding assistant with full knowledge of the user's project.")
            sb.appendLine("Project: $projectName")
            sb.appendLine()
            sb.appendLine("Project file tree:")
            files.keys.sorted().forEach { sb.appendLine("  $it") }
            sb.appendLine()
        } else {
            sb.appendLine("You are an expert coding assistant.")
            sb.appendLine()
        }

        // Always inject the currently open file's content so the model
        // understands whatever the user is looking at right now.
        val activePath    = viewModel.activeFilePath.value
        val activeContent = viewModel.activeFileContent.value
        if (activePath != null && !activeContent.isNullOrEmpty()) {
            val truncated = if (activeContent.length > 3000)
                activeContent.take(3000) + "\n...[truncated]"
            else
                activeContent
            sb.appendLine("The user currently has this file open in the editor:")
            sb.appendLine("--- $activePath ---")
            sb.appendLine(truncated)
            sb.appendLine()
        }

        sb.appendLine("When asked about a specific file by name, its content will be provided in the user message.")
        sb.appendLine("Answer accurately and concisely based on the actual code provided.")

        LlamaBridge.setModelRules(sb.toString())
    }

    // ── Message submission ────────────────────────────────────────────────────

    private fun submitUserMessage(userText: String) {
        chatOutput.append("👤 You: $userText\n\n")
        scrollToBottom()

        viewModel.addChatMessage(
            AppViewModel.ChatMessage(AppViewModel.ChatMessage.Role.USER, userText)
        )

        val fullPrompt = buildAugmentedPrompt(userText)
        executeInference(fullPrompt)
    }

    /**
     * Builds the user-turn prompt.
     *
     * Priority order for context injection:
     *   1. If the user explicitly mentions a file name → inject that file's full content
     *   2. Otherwise → just the user text
     *      (the currently open file is already in system rules via updateSystemRules)
     */
    private fun buildAugmentedPrompt(userText: String): String {
        val files      = viewModel.projectContext.value
        if (files.isNullOrEmpty()) return userText

        val lowerQuery      = userText.lowercase()
        val MAX_INLINE      = 3_500

        val matched = files.entries
            .filter { (path, _) ->
                val name = path.substringAfterLast('/')
                lowerQuery.contains(name.lowercase()) || lowerQuery.contains(path.lowercase())
            }
            .sortedBy { it.key }

        if (matched.isEmpty()) return userText

        val sb = StringBuilder()
        var total = 0
        sb.appendLine("Relevant file contents for this question:")
        for ((path, content) in matched) {
            val truncated = if (content.length + total > MAX_INLINE)
                content.take(MAX_INLINE - total) + "\n...[truncated]"
            else
                content
            sb.appendLine("--- $path ---")
            sb.appendLine(truncated)
            sb.appendLine()
            total += truncated.length
            if (total >= MAX_INLINE) break
        }
        sb.appendLine("User question: $userText")
        return sb.toString()
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    private fun executeInference(prompt: String) {
        setGeneratingState(true)

        streamBuffer.clear()
        chatOutput.append("🤖 Assistant: ")
        streamStartLen = chatOutput.text.length

        val svc = llmService
        if (svc == null) {
            chatOutput.append("[Service not ready — please wait]\n\n")
            setGeneratingState(false)
            return
        }

        // Delegate to LlmService which runs in a SupervisorJob-backed scope
        // that is NOT cancelled when the fragment goes to background.
        svc.generate(prompt, 1024, object : LlamaBridge.GenerateCallback {

            override fun onToken(piece: String) {
                streamBuffer.append(piece)
                // Post back to main thread safely even if fragment is paused
                requireActivity().runOnUiThread {
                    if (isAdded) {
                        val current = chatOutput.text as? SpannableStringBuilder
                            ?: SpannableStringBuilder(chatOutput.text)
                        if (current.length > streamStartLen) {
                            current.delete(streamStartLen, current.length)
                        }
                        current.append(streamBuffer)
                        chatOutput.text = current
                        scrollToBottom()
                    }
                }
            }

            override fun onComplete(fullResponse: String) {
                requireActivity().runOnUiThread {
                    if (isAdded) {
                        chatOutput.append("\n\n")
                        viewModel.addChatMessage(
                            AppViewModel.ChatMessage(
                                AppViewModel.ChatMessage.Role.ASSISTANT,
                                fullResponse
                            )
                        )
                        setGeneratingState(false)
                        scrollToBottom()
                    }
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "LLM error: $error")
                requireActivity().runOnUiThread {
                    if (isAdded) {
                        chatOutput.append("[Error: $error]\n\n")
                        setGeneratingState(false)
                        scrollToBottom()
                    }
                }
            }
        })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isGenerating() = llmService?.isGenerating() == true

    private fun setGeneratingState(generating: Boolean) {
        typingIndicator.visibility = if (generating) View.VISIBLE else View.GONE
        sendBtn.isEnabled  = !generating && viewModel.isModelLoaded.value == true
        inputBox.isEnabled = true
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun confirmClear() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear conversation?")
            .setMessage("Clears the chat and resets the model's conversation memory.")
            .setPositiveButton("Clear") { _, _ ->
                LlamaBridge.clearHistory()
                viewModel.clearChatHistory()
                updateSystemRules()  // Re-push system rules after KV reset
                chatOutput.text = "🤖 Conversation cleared.\n\n"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}