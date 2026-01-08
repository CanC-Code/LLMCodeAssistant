package io.canccode.aca

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.canccode.aca.databinding.FragmentFileBrowserBinding
import java.io.File

class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var adapter: FileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FileListAdapter(requireContext()) { file ->
            viewModel.selectFile(file)
            parentFragmentManager.beginTransaction()
                .replace(R.id.top_container, EditorFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        loadFiles()
    }

    private fun loadFiles() {
        val root = requireContext().filesDir
        adapter.submitList(root.listFiles()?.toList() ?: emptyList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}