package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class LLMFragment : Fragment() {

    private val TAG = "LLMFragment"
    
    private val modelUrl =
        "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
    private val modelFileName = "tinyllama-1.1b-chat.Q4_K_M.gguf"

    private var initialized = false

    private var chatOutput: TextView? = null
    private var inputBox: EditText? = null
    private var sendBtn: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            Log.d(TAG, "Creating LLMFragment view")
            inflater.inflate(R.layout.fragment_llm, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating fragment", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            Log.d(TAG, "Initializing views")
            
            chatOutput = view.findViewById(R.id.chatOutput)
            inputBox = view.findViewById(R.id.inputBox)
            sendBtn = view.findViewById(R.id.sendBtn)

            sendBtn?.setOnClickListener {
                val prompt = inputBox?.text?.toString()?.trim()
                if (!prompt.isNullOrEmpty()) {
                    inputBox?.text?.clear()
                    generateAndDisplay(prompt)
                }
            }

            chatOutput?.text = "LLM Fragment loaded. Initializing model...\n"
            
            initializeLLM()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            Toast.makeText(requireContext(), "View error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeLLM() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelDir = File(requireContext().filesDir, "models")
                if (!modelDir.exists()) modelDir.mkdirs()

                val modelFile = File(modelDir, modelFileName)

                if (!modelFile.exists()) {
                    withContext(Dispatchers.Main) {
                        chatOutput?.append("Downloading model...\n")
                    }
                    
                    try {
                        downloadModel(modelFile)
                        withContext(Dispatchers.Main) {
                            chatOutput?.append("Model downloaded!\n")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Download failed", e)
                        withContext(Dispatchers.Main) {
                            chatOutput?.append("Download failed: ${e.message}\n")
                            Toast.makeText(requireContext(), "Failed to download model", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }

                val ok = LlamaBridge.initNative(modelFile.absolutePath, 2048)
                initialized = ok

                withContext(Dispatchers.Main) {
                    val msg = if (ok) "LLM Ready! Ask me anything.\n" else "LLM Init Failed\n"
                    chatOutput?.append(msg)
                    Toast.makeText(requireContext(), if (ok) "LLM Ready" else "LLM Init Failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Init error", e)
                withContext(Dispatchers.Main) {
                    chatOutput?.append("Init error: ${e.message}\n")
                }
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
            try {
                val response = if (initialized) {
                    LlamaBridge.generateNative(prompt, 256)
                } else {
                    "[model not initialized]"
                }

                withContext(Dispatchers.Main) {
                    chatOutput?.append("\n> $prompt\n$response\n")
                    // Scroll to bottom
                    chatOutput?.let { textView ->
                        textView.scrollTo(0, textView.bottom)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation error", e)
                withContext(Dispatchers.Main) {
                    chatOutput?.append("\nError: ${e.message}\n")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (initialized) {
                LlamaBridge.shutdownNative()
                initialized = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}