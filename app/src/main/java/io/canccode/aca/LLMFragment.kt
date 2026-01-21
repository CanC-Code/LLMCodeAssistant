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

class LLMFragment : Fragment() {

    private val TAG = "LLMFragment"

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

            // Check if model is loaded
            val mainActivity = activity as? MainActivity
            if (mainActivity?.isLLMReady() == true) {
                chatOutput?.text = "LLM Ready! Ask me anything.\n"
            } else {
                chatOutput?.text = "No model loaded.\n\nGo to Settings → Pick Existing GGUF File to select your model.\n"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            Toast.makeText(requireContext(), "View error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAndDisplay(prompt: String) {
        val mainActivity = activity as? MainActivity
        
        if (mainActivity?.isLLMReady() != true) {
            chatOutput?.append("\n❌ Model not loaded. Go to Settings to select a model.\n")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                chatOutput?.let { output ->
                    withContext(Dispatchers.Main) {
                        output.append("\n> $prompt\n")
                    }
                }
                
                val response = LlamaBridge.generateNative(prompt, 256)

                withContext(Dispatchers.Main) {
                    chatOutput?.append("$response\n")
                    chatOutput?.let { textView ->
                        textView.scrollTo(0, textView.bottom)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation error", e)
                withContext(Dispatchers.Main) {
                    chatOutput?.append("\n❌ Error: ${e.message}\n")
                }
            }
        }
    }
}