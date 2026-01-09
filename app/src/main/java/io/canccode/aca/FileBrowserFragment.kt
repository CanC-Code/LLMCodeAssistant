// File: app/src/main/java/io/canccode/aca/FileBrowserFragment.kt
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

        val rootDir = requireContext().filesDir
        val files = rootDir.listFiles()?.toList() ?: emptyList()

        binding.recyclerViewFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFiles.adapter =
            FileListAdapter(files) { file ->
                // TODO: open file in editor
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}