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
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)

        recyclerView = view.findViewById(R.id.file_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val files = requireContext().filesDir
            .listFiles()
            ?.toList()
            ?: emptyList()

        adapter = FileListAdapter(
            context = requireContext(),
            files = files
        ) { file ->
            openFileInEditor(file)
        }

        recyclerView.adapter = adapter

        return view
    }

    private fun openFileInEditor(file: File) {
        val editorFragment = parentFragmentManager
            .findFragmentByTag("EDITOR") as? EditorFragment

        editorFragment?.loadFile(file)
    }
}