package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import io.canccode.aca.databinding.FragmentOutputConsoleBinding

class OutputConsoleFragment : Fragment() {

    private var _binding: FragmentOutputConsoleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOutputConsoleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun appendText(text: String) {
        binding.consoleText.append("$text\n")
        binding.consoleScroll.post { binding.consoleScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun clear() {
        binding.consoleText.text = ""
    }
}