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

    private lateinit var chatOutput: TextView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chatOutput = view.findViewById(R.id.chatOutput)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)

        chatOutput.text = "LLM ready.\n"

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener

            inputBox.text.clear()
            generate(prompt)
        }
    }

    private fun generate(prompt: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = LlamaBridge.generateNative(prompt, 256)

                withContext(Dispatchers.Main) {
                    chatOutput.append("\n> $prompt\n$response\n")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Generation failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "LLM error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // DO NOT shutdown here — MainActivity owns lifecycle
    }
}