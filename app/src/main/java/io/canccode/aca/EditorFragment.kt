package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.canccode.aca.databinding.FragmentEditorBinding

class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.editorContent.observe(viewLifecycleOwner) { content ->
            if (binding.editor.text.toString() != content) {
                binding.editor.setText(content)
            }
        }

        binding.editor.addTextChangedListener {
            viewModel.updateEditorContent(it.toString())
        }

        binding.saveButton.setOnClickListener {
            viewModel.selectedFile.value?.let { file ->
                requireContext().openFileOutput(file.name, 0).use { it.write(binding.editor.text.toString().toByteArray()) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}