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
    private var responseStartIndex: Int = 0
    private var currentResponse = StringBuilder()
    private var thinkingJob: Job? = null
    private var isGenerating: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isEmpty() || isGenerating) return@setOnClickListener
            
            // Validate model selection
            val modelFile = getSelectedModelFile()
            if (modelFile == null || !modelFile.exists()) {
                Toast.makeText(context, "Select a model in Settings first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            inputBox.text.clear()
            executeInference(prompt)
        }

        clearBtn.setOnClickListener {
            LlamaBridge.clearHistoryNative()
            chatMessages.clear()
            chatOutput.text = "🤖 Memory Cleared.\n\n"
        }
    }

    private fun executeInference(prompt: String) {
        isGenerating = true
        sendBtn.isEnabled = false
        lastPrompt = prompt
        
        chatOutput.append("👤 You: $prompt\n\n🤖 Assistant: ")
        responseStartIndex = chatOutput.text.length
        currentResponse.setLength(0)
        
        startThinkingAnimation()
        scrollToBottom()

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                LlamaBridge.generateNative(prompt, 1024, this@LLMFragment)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Native failure") }
            }
        }
    }

    override fun onToken(piece: String) {
        activity?.runOnUiThread {
            if (thinkingJob != null) stopThinkingAnimation()
            
            currentResponse.append(piece)
            val base = chatOutput.text.subSequence(0, responseStartIndex)
            chatOutput.text = SpannableStringBuilder().append(base).append(currentResponse)
            scrollToBottom()
        }
    }

    override fun onComplete(fullResponse: String) {
        activity?.runOnUiThread {
            stopThinkingAnimation()
            isGenerating = false
            sendBtn.isEnabled = true
            chatMessages.add(Pair(lastPrompt, fullResponse))
            chatOutput.append("\n\n")
            scrollToBottom()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            stopThinkingAnimation()
            isGenerating = false
            sendBtn.isEnabled = true
            chatOutput.append("\n❌ Error: $error\n\n")
        }
    }

    private fun startThinkingAnimation() {
        thinkingJob?.cancel()
        thinkingJob = lifecycleScope.launch(Dispatchers.Main) {
            var dots = 0
            while (isGenerating) {
                val dotStr = ".".repeat(dots + 1)
                val base = chatOutput.text.subSequence(0, responseStartIndex)
                chatOutput.text = SpannableStringBuilder().append(base).append("Thinking$dotStr")
                dots = (dots + 1) % 3
                delay(400)
            }
        }
    }

    private fun stopThinkingAnimation() {
        thinkingJob?.cancel()
        thinkingJob = null
    }

    private fun getSelectedModelFile(): File? {
        val prefs = requireContext().getSharedPreferences("model_prefs", 0)
        return prefs.getString("model_path", null)?.let { File(it) }
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onDestroyView() {
        stopThinkingAnimation()
        super.onDestroyView()
    }
}
