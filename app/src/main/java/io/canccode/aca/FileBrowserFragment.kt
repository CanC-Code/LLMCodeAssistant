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

        recyclerView = view.findViewById(R.id.fileRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val rootDir: File = requireContext().filesDir
        val files: List<File> = rootDir.listFiles()?.toList() ?: emptyList()

        adapter = FileListAdapter(
            context = requireContext(),
            files = files
        ) { file ->
            openFile(file)
        }

        recyclerView.adapter = adapter

        return view
    }

    private fun openFile(file: File) {
        // next step: route this to EditorFragment
    }
}