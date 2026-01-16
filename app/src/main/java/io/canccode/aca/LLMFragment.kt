package io.canccode.aca

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LLMFragment : Fragment() {

    companion object {
        private const val TAG = "LLMFragment"
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-GGUF/resolve/main/tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val MODEL_FILENAME = "tinyllama-1.1b-chat.Q4_K_M.gguf"
        private const val N_CTX = 512
    }

    private lateinit var fileRecyclerView: RecyclerView
    private lateinit var chatOutput: TextView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var llmProgressBar: ProgressBar

    private val fragmentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var modelReady = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_llm, container, false)
        fileRecyclerView = view.findViewById(R.id.fileRecyclerView)
        chatOutput = view.findViewById(R.id.chatOutput)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)
        llmProgressBar = view.findViewById(R.id.llmProgressBar)

        chatOutput.movementMethod = ScrollingMovementMethod()

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isNotEmpty() && modelReady) {
                appendChat("You: $prompt")
                generateResponse(prompt)
                inputBox.text.clear()
            } else if (!modelReady) {
                appendChat("LLM not ready yet. Please wait for initialization.")
            }
        }

        fragmentScope.launch {
            initializeLLM()
        }

        return view
    }

    private fun appendChat(text: String) {
        chatOutput.append("$text\n")
        val scrollAmount = chatOutput.layout?.getLineTop(chatOutput.lineCount) ?: 0
        if (scrollAmount > chatOutput.height) {
            chatOutput.scrollTo(0, scrollAmount - chatOutput.height)
        }
    }

    private suspend fun initializeLLM() = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(requireContext().filesDir, MODEL_FILENAME)

            // Show progress
            withContext(Dispatchers.Main) {
                llmProgressBar.visibility = ProgressBar.VISIBLE
                llmProgressBar.progress = 0
            }

            if (!modelFile.exists()) {
                Log.i(TAG, "Downloading model...")
                downloadModel(modelFile)
            } else {
                Log.i(TAG, "Model already exists: ${modelFile.absolutePath}")
            }

            Log.i(TAG, "Initializing LLM...")
            val ok = LlamaBridge.initNative(modelFile.absolutePath, N_CTX)
            modelReady = ok
            Log.i(TAG, "LLM init result = $ok")

            if (ok) {
                withContext(Dispatchers.Main) {
                    appendChat("LLM is ready!")
                }
            } else {
                withContext(Dispatchers.Main) {
                    appendChat("Failed to initialize LLM.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LLM", e)
            withContext(Dispatchers.Main) {
                appendChat("Error initializing LLM: ${e.message}")
            }
        } finally {
            withContext(Dispatchers.Main) {
                llmProgressBar.visibility = ProgressBar.GONE
            }
        }
    }

    private suspend fun downloadModel(destinationFile: File) = withContext(Dispatchers.IO) {
        val url = URL(MODEL_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("HTTP ${connection.responseCode} downloading model")
        }

        val totalSize = connection.contentLengthLong
        var downloaded = 0L

        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read

                    withContext(Dispatchers.Main) {
                        if (totalSize > 0) {
                            val progress = ((downloaded * 100) / totalSize).toInt()
                            llmProgressBar.progress = progress
                        }
                    }
                }
            }
        }
        Log.i(TAG, "Model downloaded to ${destinationFile.absolutePath}")
    }

    private fun generateResponse(prompt: String) {
        fragmentScope.launch(Dispatchers.IO) {
            try {
                val output = LlamaBridge.generateNative(prompt, 128)
                withContext(Dispatchers.Main) {
                    appendChat("LLM: $output")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating LLM response", e)
                withContext(Dispatchers.Main) {
                    appendChat("Error generating LLM response: ${e.message}")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentScope.cancel()
    }

    init {
        System.loadLibrary("llama_jni")
    }
}