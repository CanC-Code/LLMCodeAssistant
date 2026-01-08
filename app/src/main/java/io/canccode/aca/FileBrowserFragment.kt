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

    private lateinit var adapter: FileListAdapter
    private var currentDir: File = File("/sdcard")

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

        adapter = FileListAdapter(
            requireContext(),
            onClick = { file ->
                if (file.isDirectory) {
                    loadDirectory(file)
                } else {
                    (activity as? MainActivity)?.openFile(file)
                }
            }
        )

        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
        binding.fileList.adapter = adapter

        loadDirectory(currentDir)
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
        adapter.submitList(files)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}