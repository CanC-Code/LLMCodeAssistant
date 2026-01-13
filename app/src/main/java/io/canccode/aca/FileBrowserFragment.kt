package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileNodeAdapter

    private lateinit var projectLoader: ProjectLoader

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        return view
    }

    override fun onResume() {
        super.onResume()
        projectLoader = (activity as MainActivity).projectLoader

        val root = projectLoader.getRootNode()
        adapter = FileNodeAdapter(root) { node ->
            if (!node.isDirectory) {
                // Handle file click
                val editorFragment =
                    (activity as MainActivity).supportFragmentManager
                        .findFragmentByTag("EditorFragment") as? EditorFragment
                editorFragment?.let {
                    it.loadFile(node.path)
                    (activity as MainActivity).switchMode(it)
                }
            } else {
                // Toggle expanded/collapsed
                node.expanded = !node.expanded
                adapter.notifyDataSetChanged()
            }
        }
        recyclerView.adapter = adapter
    }
}