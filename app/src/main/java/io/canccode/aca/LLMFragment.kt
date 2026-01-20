package io.canccode.aca

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class LLMFragment : Fragment(R.layout.fragment_llm) {

    private val modelUrl =
        "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
    private val modelFileName = "tinyllama-1.1b-chat.Q4_K_M.gguf"

    private var initialized = false

    private lateinit var chatOutput: TextView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatOutput = view.findViewById(R.id.chatOutput)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isNotEmpty()) {
                inputBox.text.clear()
                generateAndDisplay(prompt)
            }
        }

        initializeLLM()
    }

    private fun initializeLLM() {
        lifecycleScope.launch(Dispatchers.IO) {

            val modelDir = File(requireContext().filesDir, "models")
            if (!modelDir.exists()) modelDir.mkdirs()

            val modelFile = File(modelDir, modelFileName)

            if (!modelFile.exists()) {
                try {
                    downloadModel(modelFile)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to download model", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
            }

            val ok = LlamaBridge.initNative(modelFile.absolutePath, 2048)
            initialized = ok

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), if (ok) "LLM Ready" else "LLM Init Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadModel(dest: File) {
        URL(modelUrl).openStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun generateAndDisplay(prompt: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = if (initialized) {
                LlamaBridge.generateNative(prompt, 256)
            } else {
                "[model not initialized]"
            }

            withContext(Dispatchers.Main) {
                chatOutput.append("\n> $prompt\n$response")
                // Scroll to bottom
                chatOutput.scrollTo(0, chatOutput.bottom)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (initialized) {
            LlamaBridge.shutdownNative()
            initialized = false
        }
    }
}