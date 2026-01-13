package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileListAdapter
    private var files: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        setupAdapter()
        return view
    }

    override fun onResume() {
        super.onResume()
        loadCurrentFiles()
    }

    private fun setupAdapter() {
        adapter = FileListAdapter(files) { fileName ->
            // Open EditorFragment for the clicked file
            val fragment = EditorFragment.newInstance(fileName)
            (activity as? MainActivity)?.supportFragmentManager?.commit {
                replace(R.id.contentContainer, fragment)
                addToBackStack(null)
            }
        }
        recyclerView.adapter = adapter
    }

    private fun loadCurrentFiles() {
        val mainActivity = activity as? MainActivity
        files = mainActivity?.getCurrentFileList() ?: emptyList()
        adapter.updateFiles(files)
    }
}