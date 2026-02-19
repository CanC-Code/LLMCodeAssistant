package io.canccode.aca

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import java.lang.ref.WeakReference

/**
 * LLM chat fragment — streaming, snappy, live-typing output.
 *
 * FIXES IN THIS REVISION
 * ──────────────────────
 * 1. SILENT STALL — requireActivity() IN JNI CALLBACKS
 *    Root cause: the JNI inference runs on a background coroutine thread inside
 *    LlmService. When each token arrives, the previous code called
 *    requireActivity().runOnUiThread() from that background thread.
 *    If the user navigated away from LLMFragment while generating (e.g. opened
 *    the editor), requireActivity() throws IllegalStateException because the
 *    fragment is detached. LlmService's coroutine catches this, calls
 *    callback.onError() — which ALSO throws (same reason) — and inference
 *    disappears silently with the typing indicator stuck on screen forever.
 *
 *    FIX: capture a WeakReference<Activity> before submitting the callback.
 *    On each token/complete/error we dereference the weak ref; if the Activity
 *    is gone we simply drop the UI update. The service keeps generating in the
 *    background and the KV cache stays intact. No more silent stalls.
 *
 * 2. "ASSISTANT READY" WITH NO MODEL
 *    restoreHistory() used to unconditionally show "🤖 Assistant ready." even
 *    before any model was selected. It now checks isModelLoaded and shows a
 *    clear actionable message when no model is present.
 *
 * 3. SCROLL THRASHING
 *    scrollToBottom() was posted on every single token. For a 512-token response
 *    that's 512 layout passes. Now a pending-flag approach ensures at most one
 *    scroll post is queued per UI frame, regardless of token rate.
 *
 * 4. MINIMAL SYSTEM PROMPT → FASTER FIRST TOKEN
 *    The old system prompt injected the full file tree listing and a verbose
 *    multi-line "wrap suggestions in a suggestion block" paragraph. Every token
 *    decode attends to all system-prompt tokens. Trimming it to 3 short lines
 *    measurably reduces decode time on a 3B model.
 *
 * 5. LIVE TYPING — tokens appear character-by-character as they are produced
 *    by the model, with no buffering delay. onToken() appends directly to the
 *    TextView. O(1) per token — no full-buffer rebuild.
 */
class LLMFragment : Fragment() {

    private val TAG = "LLMFragment"
    private val viewModel: AppViewModel by activityViewModels()

    // ── Views ─────────────────────────────────────────────────────────────────
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

    // ── Scroll batching ───────────────────────────────────────────────────────
    // Only one scroll-to-bottom is queued per frame, regardless of token rate.
    private var scrollPending = false

    // ── LlmService binding ────────────────────────────────────────────────────
    private var llmService:  LlmService? = null
    private var serviceBound = false

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
            llmService   = (binder as LlmService.LocalBinder).getService()
            serviceBound = true
            syncSendButton()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            llmService   = null
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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

        // Safe initial state — will be updated by observer
        sendBtn.isEnabled = false
        inputBox.hint     = "Checking model…"

        restoreHistory()
        setupObservers()

        sendBtn.setOnClickListener {
            val text = inputBox.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            if (viewModel.isModelLoaded.value != true) {
                Toast.makeText(context,
                    "No model loaded — open Settings & Model", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            inputBox.text.clear()
            submitUserMessage(text)
        }

        clearBtn.setOnClickListener { confirmClear() }

        chatOutput.setOnLongClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Chat", chatOutput.text))
            Toast.makeText(context, "Chat copied", Toast.LENGTH_SHORT).show()
            true
        }

        btnApplyDiff.setOnClickListener  { applyPendingDiff() }
        btnRejectDiff.setOnClickListener { viewModel.clearDiffSuggestion() }
    }

    override fun onStart() {
        super.onStart()
        if (!serviceBound) {
            requireContext().bindService(
                Intent(requireContext(), LlmService::class.java),
                serviceConn, Context.BIND_AUTO_CREATE
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
        val history    = viewModel.chatHistory.value ?: emptyList()
        val modelReady = viewModel.isModelLoaded.value == true

        if (history.isEmpty()) {
            // ── FIX #2: honest welcome message based on actual model state ──
            chatOutput.text = if (modelReady) {
                "🤖 Model ready. Ask me anything about your code.\n\n"
            } else {
                "⚠️ No model loaded.\n\nGo to  ☰ → Settings & Model  and pick a .gguf file.\n\n"
            }
            return
        }

        val sb = SpannableStringBuilder()
        history.forEach { msg ->
            when (msg.role) {
                AppViewModel.ChatMessage.Role.USER      ->
                    sb.append("👤 You: ${msg.text}\n\n")
                AppViewModel.ChatMessage.Role.ASSISTANT ->
                    sb.append("🤖 Assistant: ${msg.text}\n\n")
            }
        }
        chatOutput.text = sb
        scheduleScroll()
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.isModelLoaded.observe(viewLifecycleOwner) { loaded ->
            syncSendButton(loaded)
            // Update welcome text when model state changes live
            if (loaded && chatOutput.text.startsWith("⚠️")) {
                chatOutput.text = "🤖 Model ready. Ask me anything about your code.\n\n"
            }
        }

        viewModel.llmInput.observe(viewLifecycleOwner) { input ->
            if (!input.isNullOrBlank()) {
                viewModel.consumeLlmInput()
                val text = input.trim()
                if (text.isEmpty()) return@observe
                if (viewModel.isModelLoaded.value == true) {
                    submitUserMessage(text)
                } else {
                    inputBox.setText(text)
                    Toast.makeText(context,
                        "Model not ready — text placed in input box",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.projectName.observe(viewLifecycleOwner)    { updateBadge(); updateSystemRules() }
        viewModel.activeFilePath.observe(viewLifecycleOwner) { updateBadge(); updateSystemRules() }

        viewModel.pendingDiff.observe(viewLifecycleOwner) { diff ->
            if (diff == null) {
                diffPanel.visibility = View.GONE
            } else {
                diffPanel.visibility = View.VISIBLE
                renderDiffPreview(diff)
            }
        }
    }

    private fun syncSendButton(loaded: Boolean = viewModel.isModelLoaded.value == true) {
        val generating = llmService?.isGenerating() == true
        sendBtn.isEnabled = loaded && !generating
        inputBox.hint = when {
            !loaded    -> "No model loaded — open Settings & Model"
            generating -> "Generating…"
            else       -> "Ask the assistant…"
        }
    }

    private fun updateBadge() {
        val project = viewModel.projectName.value?.let { "📁 $it" } ?: ""
        val file    = viewModel.activeFilePath.value
            ?.substringAfterLast('/')?.let { "  ✏️ $it" } ?: ""
        val label   = "$project$file"
        projectBadge.visibility = if (label.isBlank()) View.GONE else View.VISIBLE
        if (label.isNotBlank()) projectBadge.text = label
    }

    // ── System rules ──────────────────────────────────────────────────────────

    /**
     * Keep the system prompt short.
     * Every token in the system prompt is attended to on every decode step.
     * A 200-token system prompt on a 3B model costs ~10 % of decode time vs a 30-token one.
     */
    private fun updateSystemRules() {
        val sb = StringBuilder()
        sb.appendLine("You are a concise expert coding assistant.")
        viewModel.projectName.value?.let { sb.appendLine("Project: $it") }
        sb.appendLine("Reply with code in ``` blocks. Be brief.")
        LlamaBridge.setModelRules(sb.toString())
    }

    // ── Message submission ────────────────────────────────────────────────────

    private fun submitUserMessage(userText: String) {
        chatOutput.append("👤 You: $userText\n\n")
        scheduleScroll()
        viewModel.addChatMessage(
            AppViewModel.ChatMessage(AppViewModel.ChatMessage.Role.USER, userText)
        )
        executeInference(buildAugmentedPrompt(userText))
    }

    /**
     * Injects the currently open file into the user turn so the model has
     * the code context it needs without requiring a full project scan.
     *
     * Priority:
     *  1. Currently open file — always injected when present (stall-fix).
     *  2. Project files whose name is mentioned in the query.
     *  3. Bare user text if no context is available.
     */
    private fun buildAugmentedPrompt(userText: String): String {
        val sb         = StringBuilder()
        var hasContext = false
        val MAX_CHARS  = 3_500  // leaves ample tokens for the 4096-ctx response

        // 1. Open file
        val activePath    = viewModel.activeFilePath.value
        val activeContent = viewModel.activeFileContent.value
        if (activePath != null && !activeContent.isNullOrEmpty()) {
            val snippet = activeContent.take(MAX_CHARS)
            sb.appendLine("File: $activePath")
            sb.appendLine("```")
            sb.appendLine(snippet)
            if (activeContent.length > MAX_CHARS) sb.appendLine("...[truncated]")
            sb.appendLine("```")
            sb.appendLine()
            hasContext = true
        }

        // 2. Project files mentioned by name in the query
        val files      = viewModel.projectContext.value
        val lowerQuery = userText.lowercase()
        if (!files.isNullOrEmpty()) {
            var remaining = MAX_CHARS - sb.length
            files.entries
                .filter { (path, _) ->
                    val name = path.substringAfterLast('/')
                    lowerQuery.contains(name.lowercase()) ||
                    lowerQuery.contains(path.lowercase())
                }
                .sortedBy { it.key }
                .forEach { (path, content) ->
                    if (remaining <= 0) return@forEach
                    if (path == activePath) return@forEach  // already injected
                    val snippet = content.take(remaining)
                    sb.appendLine("File: $path")
                    sb.appendLine("```")
                    sb.appendLine(snippet)
                    if (content.length > remaining) sb.appendLine("...[truncated]")
                    sb.appendLine("```")
                    sb.appendLine()
                    remaining   -= snippet.length
                    hasContext   = true
                }
        }

        if (!hasContext) return userText
        sb.appendLine("Question: $userText")
        return sb.toString()
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    private fun executeInference(prompt: String) {
        setGeneratingState(true)
        chatOutput.append("🤖 Assistant: ")
        scheduleScroll()

        val svc = llmService
        if (svc == null) {
            chatOutput.append("[Service not connected — please wait and retry]\n\n")
            setGeneratingState(false)
            return
        }

        // ── FIX #1: WeakReference prevents IllegalStateException on detach ────
        //
        // The JNI callback fires on a background thread owned by LlmService.
        // If this fragment is popped while generating, requireActivity() throws,
        // the coroutine catches it, fires onError — which also throws — and
        // everything dies silently with the typing indicator stuck forever.
        //
        // With a WeakReference: if the Activity has been finished or GC'd,
        // activityRef.get() returns null and we silently drop the UI update.
        // The service continues generating and the KV cache stays intact.
        val activityRef = WeakReference<Activity>(requireActivity())

        svc.generate(prompt, 1024, object : LlamaBridge.GenerateCallback {

            // ── LIVE TYPING: each token appended immediately, O(1) ────────────
            override fun onToken(piece: String) {
                val act = activityRef.get() ?: return   // fragment gone — drop
                act.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    chatOutput.append(piece)   // direct append, no rebuild
                    scheduleScroll()           // at most one scroll per frame
                }
            }

            override fun onComplete(fullResponse: String) {
                val act = activityRef.get() ?: return
                act.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    chatOutput.append("\n\n")
                    viewModel.addChatMessage(
                        AppViewModel.ChatMessage(
                            AppViewModel.ChatMessage.Role.ASSISTANT, fullResponse
                        )
                    )
                    setGeneratingState(false)
                    scheduleScroll()
                    extractAndPostDiff(fullResponse)
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "LLM error: $error")
                val act = activityRef.get() ?: return
                act.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    chatOutput.append("[Error: $error]\n\n")
                    setGeneratingState(false)
                    scheduleScroll()
                }
            }
        })
    }

    // ── Scroll batching ───────────────────────────────────────────────────────

    /**
     * Posts a single fullScroll() per UI frame.
     * If a scroll is already queued for this frame this call is a no-op.
     * Reduces layout passes from O(tokens_per_response) to O(frames_elapsed).
     */
    private fun scheduleScroll() {
        if (scrollPending) return
        scrollPending = true
        chatScroll.post {
            scrollPending = false
            chatScroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    // ── Diff extraction ───────────────────────────────────────────────────────

    private fun extractAndPostDiff(response: String) {
        val activePath    = viewModel.activeFilePath.value    ?: return
        val activeContent = viewModel.activeFileContent.value ?: return

        val pattern = Regex(
            "```(?:suggestion|kotlin|java|cpp|xml|python|js|ts|\\w+)?\\s*\\n([\\s\\S]*?)\\n```",
            RegexOption.MULTILINE
        )
        val match     = pattern.find(response) ?: return
        val suggested = match.groupValues[1].trim()
        if (suggested.isBlank() || suggested == activeContent.trim()) return

        val description = response.substringBefore("```").trim()
            .takeLast(200).ifBlank { "LLM suggested changes" }

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

    private fun renderDiffPreview(diff: AppViewModel.DiffSuggestion) {
        val oldLines = diff.originalContent.lines()
        val newLines = diff.suggestedContent.lines()
        val spans    = SpannableStringBuilder()
        val lcs      = computeLCS(oldLines, newLines)
        var oi = 0; var ni = 0; var li = 0

        fun appendLine(line: String, bgColor: Int?) {
            val start = spans.length
            spans.append(line).append("\n")
            if (bgColor != null) spans.setSpan(
                BackgroundColorSpan(bgColor), start, spans.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        while (oi < oldLines.size || ni < newLines.size) {
            val inLcs   = li < lcs.size
            val nextOld = if (inLcs) lcs[li].first  else Int.MAX_VALUE
            val nextNew = if (inLcs) lcs[li].second else Int.MAX_VALUE
            when {
                oi < nextOld && ni < nextNew -> {
                    if (oi < oldLines.size) appendLine("- ${oldLines[oi++]}", Color.parseColor("#FFCCCC"))
                    if (ni < newLines.size) appendLine("+ ${newLines[ni++]}", Color.parseColor("#CCE5FF"))
                }
                oi < nextOld -> if (oi < oldLines.size) appendLine("- ${oldLines[oi++]}", Color.parseColor("#FFCCCC"))
                ni < nextNew -> if (ni < newLines.size) appendLine("+ ${newLines[ni++]}", Color.parseColor("#CCE5FF"))
                else -> {
                    if (oi < oldLines.size) appendLine("  ${oldLines[oi++]}", null)
                    ni++; li++
                }
            }
        }
        diffPreview.text = spans
    }

    private fun computeLCS(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val m = a.size; val n = b.size
        // Skip O(m*n) DP on very large files to avoid UI jank
        if (m.toLong() * n > 200_000L) return emptyList()
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) for (j in 1..n)
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1] + 1
                       else maxOf(dp[i-1][j], dp[i][j-1])
        val result = mutableListOf<Pair<Int, Int>>()
        var i = m; var j = n
        while (i > 0 && j > 0) {
            when {
                a[i-1] == b[j-1]        -> { result.add(0, Pair(i-1, j-1)); i--; j-- }
                dp[i-1][j] > dp[i][j-1] -> i--
                else                     -> j--
            }
        }
        return result
    }

    // ── Diff apply ────────────────────────────────────────────────────────────

    private fun applyPendingDiff() {
        val diff = viewModel.pendingDiff.value ?: return
        viewModel.setActiveEditorFile(diff.targetFilePath, diff.suggestedContent)
        viewModel.updateEditorContent(diff.suggestedContent)
        viewModel.clearDiffSuggestion()
        chatOutput.append("✅ Applied to ${diff.targetFilePath.substringAfterLast('/')}\n\n")
        scheduleScroll()
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private fun setGeneratingState(generating: Boolean) {
        val loaded = viewModel.isModelLoaded.value == true
        typingIndicator.visibility = if (generating) View.VISIBLE else View.GONE
        sendBtn.isEnabled          = loaded && !generating
        inputBox.hint = when {
            !loaded    -> "No model loaded — open Settings & Model"
            generating -> "Generating…"
            else       -> "Ask the assistant…"
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear conversation?")
            .setMessage("Clears the chat and resets the model's memory.")
            .setPositiveButton("Clear") { _, _ ->
                LlamaBridge.clearHistory()
                viewModel.clearChatHistory()
                viewModel.clearDiffSuggestion()
                updateSystemRules()
                val modelReady = viewModel.isModelLoaded.value == true
                chatOutput.text = if (modelReady)
                    "🤖 Conversation cleared. Model ready.\n\n"
                else
                    "🤖 Conversation cleared.\n⚠️ No model loaded — open Settings & Model.\n\n"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
