package io.canccode.aca

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * LLM chat fragment.
 *
 * Key fixes vs original:
 *
 * 1. STALL FIX — active file content is ALWAYS injected into the user-turn prompt,
 *    not just when a project is loaded. The original only checked projectContext
 *    (empty unless the user loaded a full project via SAF), so asking about an open
 *    file sent a context-free prompt and the model had nothing to work with.
 *
 * 2. STREAMING EFFICIENCY — tokens are appended to a dedicated streaming TextView
 *    instead of rebuilding the whole SpannableStringBuilder on every token.
 *    The original delete/re-append loop was O(history length) per token.
 *
 * 3. DIFF EXTRACTION — when the model's response contains a code block, it is
 *    parsed and posted as a DiffSuggestion to AppViewModel.  EnhancedEditorFragment
 *    observes this and renders the diff with blue/red/green highlighting.
 *
 * 4. APPLY / REJECT — after a diff is posted, inline buttons let the user
 *    accept (writes to editor + clears diff) or reject (clears diff only).
 */
class LLMFragment : Fragment() {

    private val TAG = "LLMFragment"

    private val viewModel: AppViewModel by activityViewModels()

    // Views
    private lateinit var chatOutput:      TextView
    private lateinit var chatScroll:      ScrollView
    private lateinit var inputBox:        EditText
    private lateinit var sendBtn:         Button
    private lateinit var clearBtn:        Button
    private lateinit var typingIndicator: TextView
    private lateinit var projectBadge:    TextView
    private lateinit var diffPanel:       LinearLayout
    private lateinit var diffPreview:     TextView
    private lateinit var btnApplyDiff:    Button
    private lateinit var btnRejectDiff:   Button

    // Streaming state — we append to chatOutput only, never re-build
    private val streamBuffer    = StringBuilder()
    private var streamStartPos  = 0      // char position in chatOutput where streaming began

    // LlmService binding
    private var llmService: LlmService? = null
    private var serviceBound = false

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
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
        diffPanel       = view.findViewById(R.id.diffPanel)
        diffPreview     = view.findViewById(R.id.diffPreview)
        btnApplyDiff    = view.findViewById(R.id.btnApplyDiff)
        btnRejectDiff   = view.findViewById(R.id.btnRejectDiff)

        bindService()
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
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Chat", chatOutput.text))
            Toast.makeText(context, "Chat copied", Toast.LENGTH_SHORT).show()
            true
        }

        btnApplyDiff.setOnClickListener  { applyPendingDiff() }
        btnRejectDiff.setOnClickListener { viewModel.clearDiffSuggestion() }
    }

    private fun bindService() {
        requireContext().bindService(
            Intent(requireContext(), LlmService::class.java),
            serviceConn, Context.BIND_AUTO_CREATE
        )
    }

    override fun onStart() {
        super.onStart()
        if (!serviceBound) bindService()
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

        viewModel.llmInput.observe(viewLifecycleOwner) { input ->
            if (!input.isNullOrBlank()) {
                viewModel.consumeLlmInput()
                val text = input.trim()
                if (text.isNotEmpty() && viewModel.isModelLoaded.value == true) {
                    submitUserMessage(text)
                } else {
                    inputBox.setText(text)
                }
            }
        }

        viewModel.projectName.observe(viewLifecycleOwner) { name ->
            updateBadge()
            updateSystemRules()
        }

        viewModel.activeFilePath.observe(viewLifecycleOwner) { _ ->
            updateBadge()
            updateSystemRules()
        }

        // Diff panel visibility driven by ViewModel state
        viewModel.pendingDiff.observe(viewLifecycleOwner) { diff ->
            if (diff == null) {
                diffPanel.visibility = View.GONE
            } else {
                diffPanel.visibility = View.VISIBLE
                renderDiffPreview(diff)
            }
        }
    }

    private fun updateBadge() {
        val projectPart  = viewModel.projectName.value?.let { "📁 $it" } ?: ""
        val filePart     = viewModel.activeFilePath.value
            ?.substringAfterLast('/')
            ?.let { "  ✏️ $it" } ?: ""
        val label = "$projectPart$filePart"
        if (label.isBlank()) {
            projectBadge.visibility = View.GONE
        } else {
            projectBadge.visibility = View.VISIBLE
            projectBadge.text = label
        }
    }

    // ── System rules ──────────────────────────────────────────────────────────

    /**
     * Builds and pushes the system prompt.
     * Only the file tree listing goes here — actual file content is injected
     * per-turn in buildAugmentedPrompt() so it always reaches the model.
     */
    private fun updateSystemRules() {
        val sb = StringBuilder()
        val projectName = viewModel.projectName.value
        val files       = viewModel.projectContext.value ?: emptyMap()

        if (projectName != null && files.isNotEmpty()) {
            sb.appendLine("You are an expert coding assistant with full knowledge of the user's project.")
            sb.appendLine("Project: $projectName")
            sb.appendLine("Project file tree:")
            files.keys.sorted().forEach { sb.appendLine("  $it") }
            sb.appendLine()
        } else {
            sb.appendLine("You are an expert coding assistant.")
        }

        sb.appendLine()
        sb.appendLine("When suggesting code changes, wrap the ENTIRE replacement file content in")
        sb.appendLine("a single ```suggestion ... ``` block so the editor can show a diff.")
        sb.appendLine("Keep explanations outside the code block.")

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
     * Builds the user-turn prompt injected into the LLM.
     *
     * Priority:
     *  1. Always inject the currently open file if there is one —
     *     this is the fix for the stalling bug. The open file lives in
     *     activeFileContent, NOT in projectContext, so the original check
     *     `if (files.isNullOrEmpty()) return userText` meant the model
     *     never saw any code when the user was asking about an open file
     *     without having loaded a full project.
     *  2. Additionally inject any project files whose name is mentioned.
     */
    private fun buildAugmentedPrompt(userText: String): String {
        val sb          = StringBuilder()
        var hasContext  = false
        val MAX_TOTAL   = 4_000   // chars — keep well under context window

        // 1. Open file (always inject when present)
        val activePath    = viewModel.activeFilePath.value
        val activeContent = viewModel.activeFileContent.value
        if (activePath != null && !activeContent.isNullOrEmpty()) {
            val truncated = activeContent.take(MAX_TOTAL)
            sb.appendLine("Currently open file: $activePath")
            sb.appendLine("```")
            sb.appendLine(truncated)
            if (activeContent.length > MAX_TOTAL) sb.appendLine("...[truncated]")
            sb.appendLine("```")
            sb.appendLine()
            hasContext = true
        }

        // 2. Project files mentioned by name in the query
        val files      = viewModel.projectContext.value
        val lowerQuery = userText.lowercase()
        if (!files.isNullOrEmpty()) {
            var remaining = MAX_TOTAL - sb.length
            files.entries
                .filter { (path, _) ->
                    val name = path.substringAfterLast('/')
                    lowerQuery.contains(name.lowercase()) ||
                    lowerQuery.contains(path.lowercase())
                }
                .sortedBy { it.key }
                .forEach { (path, content) ->
                    if (remaining <= 0) return@forEach
                    // Don't re-inject the already-open file
                    if (path == activePath || path.substringAfterLast('/') ==
                        activePath?.substringAfterLast('/')) return@forEach
                    val truncated = content.take(remaining)
                    sb.appendLine("File: $path")
                    sb.appendLine("```")
                    sb.appendLine(truncated)
                    if (content.length > remaining) sb.appendLine("...[truncated]")
                    sb.appendLine("```")
                    sb.appendLine()
                    remaining -= truncated.length
                    hasContext = true
                }
        }

        if (!hasContext) return userText

        sb.appendLine("User question: $userText")
        return sb.toString()
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    private fun executeInference(prompt: String) {
        setGeneratingState(true)

        // Append assistant prefix — record where streaming will begin
        val prefix = "🤖 Assistant: "
        chatOutput.append(prefix)
        streamBuffer.clear()
        streamStartPos = chatOutput.text.length
        scrollToBottom()

        val svc = llmService
        if (svc == null) {
            chatOutput.append("[Service not ready — please wait]\n\n")
            setGeneratingState(false)
            return
        }

        svc.generate(prompt, 1024, object : LlamaBridge.GenerateCallback {

            override fun onToken(piece: String) {
                streamBuffer.append(piece)
                // Efficient: just append the new piece — no full rebuild
                requireActivity().runOnUiThread {
                    if (isAdded) {
                        chatOutput.append(piece)
                        scrollToBottom()
                    }
                }
            }

            override fun onComplete(fullResponse: String) {
                requireActivity().runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    chatOutput.append("\n\n")
                    viewModel.addChatMessage(
                        AppViewModel.ChatMessage(
                            AppViewModel.ChatMessage.Role.ASSISTANT, fullResponse
                        )
                    )
                    setGeneratingState(false)
                    scrollToBottom()

                    // Parse suggestion blocks from the response
                    extractAndPostDiff(fullResponse)
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

    // ── Diff extraction ───────────────────────────────────────────────────────

    /**
     * Looks for a ```suggestion ... ``` block in the LLM response.
     * If found, and there is an active open file, posts a DiffSuggestion.
     */
    private fun extractAndPostDiff(response: String) {
        val activePath    = viewModel.activeFilePath.value    ?: return
        val activeContent = viewModel.activeFileContent.value ?: return

        // Match ```suggestion\n...\n``` or ```\n...\n``` (fallback)
        val pattern = Regex(
            "```(?:suggestion|kotlin|java|cpp|xml|python|js|ts)?\\s*\\n([\\s\\S]*?)\\n```",
            RegexOption.MULTILINE
        )
        val match = pattern.find(response) ?: return
        val suggested = match.groupValues[1].trim()
        if (suggested.isBlank() || suggested == activeContent.trim()) return

        // Extract a description: everything before the first code fence
        val description = response.substringBefore("```").trim()
            .takeLast(200)
            .ifBlank { "LLM suggested changes" }

        viewModel.postDiffSuggestion(
            AppViewModel.DiffSuggestion(
                originalContent  = activeContent,
                suggestedContent = suggested,
                description      = description,
                targetFilePath   = activePath
            )
        )
    }

    // ── Diff panel rendering ──────────────────────────────────────────────────

    /**
     * Renders a line-by-line diff in the diffPreview TextView.
     *
     * Color coding:
     *  - Blue background  (#CCE5FF) = addition / changed line (new version)
     *  - Red  background  (#FFCCCC) = deletion (old line not in new version)
     *  - No highlight             = unchanged context
     */
    private fun renderDiffPreview(diff: AppViewModel.DiffSuggestion) {
        val oldLines = diff.originalContent.lines()
        val newLines = diff.suggestedContent.lines()

        // Simple Myers-like LCS diff (line level)
        val spans = SpannableStringBuilder()
        val lcs   = computeLCS(oldLines, newLines)
        var oi = 0; var ni = 0; var li = 0

        fun appendLine(line: String, bgColor: Int?) {
            val start = spans.length
            spans.append(line).append("\n")
            if (bgColor != null) {
                spans.setSpan(
                    BackgroundColorSpan(bgColor),
                    start, spans.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        while (oi < oldLines.size || ni < newLines.size) {
            val inLcs = li < lcs.size
            val nextOld = if (inLcs) lcs[li].first  else Int.MAX_VALUE
            val nextNew = if (inLcs) lcs[li].second else Int.MAX_VALUE

            when {
                oi < nextOld && ni < nextNew -> {
                    // Both changed — show deletion then addition
                    if (oi < oldLines.size) { appendLine("- ${oldLines[oi++]}", Color.parseColor("#FFCCCC")) }
                    if (ni < newLines.size) { appendLine("+ ${newLines[ni++]}", Color.parseColor("#CCE5FF")) }
                }
                oi < nextOld -> {
                    if (oi < oldLines.size) { appendLine("- ${oldLines[oi++]}", Color.parseColor("#FFCCCC")) }
                }
                ni < nextNew -> {
                    if (ni < newLines.size) { appendLine("+ ${newLines[ni++]}", Color.parseColor("#CCE5FF")) }
                }
                else -> {
                    // Matched context line
                    if (oi < oldLines.size) appendLine("  ${oldLines[oi++]}", null)
                    ni++; li++
                }
            }
        }

        diffPreview.text = spans
    }

    /** Returns indices (oldIdx, newIdx) of the longest common subsequence lines. */
    private fun computeLCS(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val m = a.size; val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1] + 1
                       else maxOf(dp[i-1][j], dp[i][j-1])
        }
        // Backtrack
        val result = mutableListOf<Pair<Int, Int>>()
        var i = m; var j = n
        while (i > 0 && j > 0) {
            when {
                a[i-1] == b[j-1] -> { result.add(0, Pair(i-1, j-1)); i--; j-- }
                dp[i-1][j] > dp[i][j-1] -> i--
                else -> j--
            }
        }
        return result
    }

    // ── Diff apply ────────────────────────────────────────────────────────────

    private fun applyPendingDiff() {
        val diff = viewModel.pendingDiff.value ?: return
        // Update the ViewModel — EnhancedEditorFragment observes editorContent
        viewModel.setActiveEditorFile(diff.targetFilePath, diff.suggestedContent)
        viewModel.updateEditorContent(diff.suggestedContent)
        viewModel.clearDiffSuggestion()
        chatOutput.append("✅ Changes applied to ${diff.targetFilePath.substringAfterLast('/')}\n\n")
        scrollToBottom()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isGenerating() = llmService?.isGenerating() == true

    private fun setGeneratingState(generating: Boolean) {
        typingIndicator.visibility = if (generating) View.VISIBLE else View.GONE
        sendBtn.isEnabled = !generating && viewModel.isModelLoaded.value == true
        inputBox.isEnabled = true
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
    }

    private fun confirmClear() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear conversation?")
            .setMessage("Clears the chat and resets the model's conversation memory.")
            .setPositiveButton("Clear") { _, _ ->
                LlamaBridge.clearHistory()
                viewModel.clearChatHistory()
                viewModel.clearDiffSuggestion()
                updateSystemRules()
                chatOutput.text = "🤖 Conversation cleared.\n\n"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
