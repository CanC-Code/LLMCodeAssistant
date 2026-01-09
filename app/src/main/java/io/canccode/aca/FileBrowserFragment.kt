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
    private val directoryPath: String = "/some/path" // adjust path

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewFiles) // make sure your XML has this ID
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val files = File(directoryPath).listFiles()?.toList() ?: emptyList()
        val fileNames: List<String> = files.map { it.name }

        adapter = FileListAdapter(fileNames) { fileName ->
            // handle file click
        }
        recyclerView.adapter = adapter

        return view
    }
}