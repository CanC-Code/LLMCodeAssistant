package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.canccode.aca.databinding.FragmentFileBrowserBinding

class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!

    private lateinit var fileListAdapter: FileListAdapter

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

        fileListAdapter = FileListAdapter()

        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileListAdapter
        }

        // Example: load files
        fileListAdapter.submitList(listOf()) // populate your file list here
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}