// File: app/src/main/java/io/canccode/aca/LLMFragment.kt
// Author: CCVO
// Purpose: Chat UI fragment for local LLM interaction
// Copyright: CanC-code - CCVO

package io.canccode.aca

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class LLMFragment : Fragment(), LlamaBridge.GenerateCallback {

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button

    private val responseBuilder = StringBuilder()
    private var responseStart = 0
    private var thinkingJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chatOutput = view.findViewById(R.id.chatOutput)
        chatScroll = view.findViewById(R.id.chatScroll)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isNotEmpty()) {
                generate(prompt)
            }
        }
    }

    private fun generate(prompt: String) {
        sendBtn.isEnabled = false
        inputBox.isEnabled = false

        chatOutput.append("👤 $prompt\n\n🤖 ")
        responseStart = chatOutput.text.length
        responseBuilder.clear()

        startThinking()

        lifecycleScope.launch(Dispatchers.IO) {
            LlamaBridge.generateNative(prompt, 512, this@LLMFragment)
        }
    }

    override fun onToken(piece: String) {
        activity?.runOnUiThread {
            stopThinking()
            responseBuilder.append(piece)
            chatOutput.text =
                chatOutput.text.substring(0, responseStart) + responseBuilder.toString()
            scroll()
        }
    }

    override fun onComplete(fullResponse: String) {
        activity?.runOnUiThread {
            stopThinking()
            chatOutput.append("\n\n")
            sendBtn.isEnabled = true
            inputBox.isEnabled = true
            inputBox.text.clear()
            scroll()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            stopThinking()
            chatOutput.append("❌ $error\n\n")
            sendBtn.isEnabled = true
            inputBox.isEnabled = true
            scroll()
        }
    }

    private fun startThinking() {
        thinkingJob?.cancel()
        thinkingJob = lifecycleScope.launch(Dispatchers.Main) {
            val dots = listOf(".", "..", "...")
            var i = 0
            while (isActive) {
                chatOutput.text =
                    chatOutput.text.substring(0, responseStart) + dots[i % dots.size]
                i++
                delay(400)
            }
        }
    }

    private fun stopThinking() {
        thinkingJob?.cancel()
        thinkingJob = null
    }

    private fun scroll() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }
}