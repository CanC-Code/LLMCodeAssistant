package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_file_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.file_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FileListAdapter(requireContext()) { file ->
            (activity as? MainActivity)?.openFileInEditor(file)
        }

        recyclerView.adapter = adapter

        loadFiles()
    }

    private fun loadFiles() {
        val root = requireContext().filesDir
        val files = root.listFiles()?.toList() ?: emptyList()
        adapter.submitList(files)
    }
}