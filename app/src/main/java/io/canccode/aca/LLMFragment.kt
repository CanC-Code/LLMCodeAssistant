package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.canccode.aca.databinding.FragmentLlmBinding

class LLMFragment : Fragment() {

    private var _binding: FragmentLlmBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LLMViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLlmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.output.observe(viewLifecycleOwner) { text ->
            binding.llmOutputText.text = text
        }

        binding.llmSendButton.setOnClickListener {
            val prompt = binding.llmInputText.text.toString()
            viewModel.setOutput("You typed: $prompt") // temporary echo
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}