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

    private val viewModel: AppViewModel by activityViewModels() // fixed

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLlmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Example: observe ViewModel data
        viewModel.someLiveData.observe(viewLifecycleOwner) { data ->
            binding.llmOutputText.text = data
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}