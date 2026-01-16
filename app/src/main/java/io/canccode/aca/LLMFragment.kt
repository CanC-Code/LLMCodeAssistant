// File: app/src/main/java/io/canccode/aca/LLMFragment.kt
package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.llmassistant.llm.LLMHandler

class LLMFragment : Fragment() {

    private lateinit var inputEditText: EditText
    private lateinit var outputTextView: TextView
    private lateinit var generateButton: Button

    companion object {
        private const val TAG = "LLMFragment"
        private const val DEFAULT_MAX_TOKENS = 64
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_llm, container, false)

        inputEditText = view.findViewById(R.id.llm_input)
        outputTextView = view.findViewById(R.id.llm_output)
        generateButton = view.findViewById(R.id.llm_generate_button)

        generateButton.setOnClickListener {
            val prompt = inputEditText.text.toString()
            if (prompt.isBlank()) return@setOnClickListener

            LLMHandler.generate(prompt, DEFAULT_MAX_TOKENS) { output ->
                outputTextView.text = output
                Log.i(TAG, "Generated output: $output")
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.i(TAG, "LLMFragment destroyed")
    }
}