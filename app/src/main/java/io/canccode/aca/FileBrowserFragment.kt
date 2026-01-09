package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.canccode.aca.databinding.FragmentFileBrowserBinding

class FileBrowserFragment(
    private val files: List<String>,
    private val onClick: (String) -> Unit
) : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileAdapter

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

        binding.recyclerViewFiles.layoutManager = LinearLayoutManager(context)
        adapter = FileAdapter(files, onClick)
        binding.recyclerViewFiles.adapter = adapter
        adapter.submitList(files)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}