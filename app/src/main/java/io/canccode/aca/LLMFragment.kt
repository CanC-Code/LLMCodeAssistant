package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.canccode.aca.llm.LLMHandler
import io.canccode.aca.R

class LLMFragment : Fragment() {

    private lateinit var llmHandler: LLMHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Instantiate the Kotlin LLMHandler (ensure correct import)
        llmHandler = LLMHandler(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the LLM fragment layout
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }
}