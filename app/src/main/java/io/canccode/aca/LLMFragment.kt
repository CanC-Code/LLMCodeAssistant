package io.canccode.aca

import android.os.Bundle
import android.view.View
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

    private val modelFileName =
        "tinyllama-1.1b-chat.Q4_K_M.gguf"

    private var initialized = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeLLM()
    }

    private fun initializeLLM() {
        lifecycleScope.launch(Dispatchers.IO) {

            val modelDir = File(requireContext().filesDir, "models")
            if (!modelDir.exists()) modelDir.mkdirs()

            val modelFile = File(modelDir, modelFileName)

            if (!modelFile.exists()) {
                downloadModel(modelFile)
            }

            val ok = LlamaBridge.initNative(
                modelFile.absolutePath,
                2048
            )

            initialized = ok
        }
    }

    private fun downloadModel(dest: File) {
        URL(modelUrl).openStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun generate(prompt: String, maxTokens: Int = 256): String {
        if (!initialized) {
            return "[model not initialized]"
        }
        return LlamaBridge.generateNative(prompt, maxTokens)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (initialized) {
            LlamaBridge.shutdownNative()
            initialized = false
        }
    }
}