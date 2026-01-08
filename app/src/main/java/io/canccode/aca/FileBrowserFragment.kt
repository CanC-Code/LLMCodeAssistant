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
    private var currentDir: File = File("/sdcard") // safe default

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

        adapter = FileListAdapter(requireContext()) { file ->
            if (file.isDirectory) {
                openDirectory(file)
            } else {
                openFile(file)
            }
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FileBrowserFragment.adapter
        }

        openDirectory(currentDir)
    }

    private fun openDirectory(dir: File) {
        currentDir = dir
        val files = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

        adapter.submitList(files)
    }

    private fun openFile(file: File) {
        // Stub for now — editor wiring comes next
        // This is intentionally empty but VALID
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}