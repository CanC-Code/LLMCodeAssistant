package io.canccode.aca.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.canccode.aca.R
import io.canccode.aca.adapters.FileListAdapter

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FileListAdapter(getCurrentFileList()) { fileName ->
            openFile(fileName)
        }
        recyclerView.adapter = adapter

        return view
    }

    private fun openFile(fileName: String) {
        val fragment = EditorFragment.newInstance(fileName)
        parentFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun getCurrentFileList(): List<String> {
        return listOf("Example1.txt", "Example2.txt", "Example3.txt")
    }
}