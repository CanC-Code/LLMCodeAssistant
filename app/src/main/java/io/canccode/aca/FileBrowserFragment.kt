package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileListAdapter
    private var files: List<String> = emptyList()

    private lateinit var projectLoader: ProjectLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectLoader = ProjectLoader(requireContext())
        files = projectLoader.getCurrentFileList() // Load existing project files if any
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_file_browser, container, false)
        recyclerView = rootView.findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FileListAdapter(files) { fileName ->
            openFile(fileName)
        }
        recyclerView.adapter = adapter

        return rootView
    }

    override fun onResume() {
        super.onResume()
        refreshFileList()
    }

    private fun refreshFileList() {
        files = projectLoader.getCurrentFileList()
        adapter.updateFiles(files)
    }

    private fun openFile(fileName: String) {
        // Handle opening the file in editor
        val activity = requireActivity() as? MainActivity
        activity?.let {
            it.switchMode(EditorFragment.newInstance(fileName))
        }
    }

}