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

    private lateinit var adapter: FileListAdapter
    private val fileList = mutableListOf<String>() // Example: file names

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

        adapter = FileListAdapter(fileList) { fileName ->
            // Handle file click: open editor fragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, EditorFragment.newInstance(fileName))
                .addToBackStack(null)
                .commit()
        }

        binding.recyclerViewFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFiles.adapter = adapter

        loadFiles()
    }

    private fun loadFiles() {
        // Populate fileList with actual files
        fileList.clear()
        fileList.addAll(listOf("File1.txt", "File2.txt", "Example.kt")) // Placeholder
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}