package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.canccode.aca.databinding.FragmentFileBrowserBinding
import java.io.File

class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val files = requireContext().filesDir.listFiles()?.toList() ?: emptyList()

        binding.fileRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.fileRecyclerView.adapter = FileListAdapter(files) { file ->
            openFile(file)
        }
    }

    private fun openFile(file: File) {
        val editorFragment = EditorFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, editorFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}