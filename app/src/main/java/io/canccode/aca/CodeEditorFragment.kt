package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import io.canccode.aca.databinding.FragmentCodeEditorBinding
import java.io.File

class CodeEditorFragment : Fragment() {

    private var _binding: FragmentCodeEditorBinding? = null
    private val binding get() = _binding!!

    // Track horizontal scroll
    val isHorizontallyScrolling: Boolean
        get() = binding.editor.scrollX > 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCodeEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun loadFile(file: File) {
        binding.editor.setText(file.readText())
    }
}