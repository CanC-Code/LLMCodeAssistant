package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

    private val responseBuilder = StringBuilder()
    private var thinkingJob: Job? = null
    private var lastResponseStartPos = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatOutput = view.findViewById(R.id.chatOutput)
        chatScroll  = view.findViewById(R.id.chatScroll)
        inputBox    = view.findViewById(R.id.inputBox)
        sendBtn     = view.findViewById(R.id.sendBtn)

        chatOutput.text = "LLM ready. Type a message.\n\n"

        sendBtn.setOnClickListener {
            val text = inputBox.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            appendMessage("You: $text\n\n")
            inputBox.text.clear()

            startThinkingAnimation()
            sendBtn.isEnabled = false
            inputBox.isEnabled = false

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    LlamaBridge.generateNative(text, 768, this@LLMFragment)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        stopThinking()
                        appendMessage("Error: ${e.message}\n\n")
                        sendBtn.isEnabled = true
                        inputBox.isEnabled = true
                    }
                }
            }
        }
    }

    // ────────────────────────────────────────────────
    //  LlamaBridge.GenerateCallback implementation
    // ────────────────────────────────────────────────

    override fun onToken(piece: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            stopThinking()
            responseBuilder.append(piece)
            chatOutput.text = chatOutput.text.toString() + piece
            scrollToBottom()
        }
    }

    override fun onComplete(fullResponse: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            stopThinking()
            appendMessage("LLM: ${responseBuilder}\n\n")
            responseBuilder.clear()
            sendBtn.isEnabled = true
            inputBox.isEnabled = true
            inputBox.requestFocus()
            scrollToBottom()
        }
    }

    override fun onError(error: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            stopThinking()
            appendMessage("Error: $error\n\n")
            sendBtn.isEnabled = true
            inputBox.isEnabled = true
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
        }
    }

    // ────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────

    private fun appendMessage(msg: String) {
        chatOutput.append(msg)
        scrollToBottom()
    }

    private fun startThinkingAnimation() {
        stopThinking()
        lastResponseStartPos = chatOutput.text.length
        responseBuilder.clear()

        thinkingJob = lifecycleScope.launch {
            var dots = ""
            while (true) {
                dots = when (dots) {
                    "" -> "."; "." -> ".."; ".." -> "..."; else -> ""
                }
                withContext(Dispatchers.Main) {
                    val base = chatOutput.text.substring(0, lastResponseStartPos)
                    chatOutput.text = base + "Thinking$dots"
                    scrollToBottom()
                }
                delay(450)
            }
        }
    }

    private fun stopThinking() {
        thinkingJob?.cancel()
        thinkingJob = null
    }

    private fun scrollToBottom() {
        chatScroll.post {
            chatScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        stopThinking()
        super.onDestroyView()
    }
}