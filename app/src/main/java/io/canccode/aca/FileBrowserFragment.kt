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
    private lateinit var projectLoader: ProjectLoader

    companion object {
        fun newInstance(loader: ProjectLoader): FileBrowserFragment {
            return FileBrowserFragment().apply {
                this.projectLoader = loader
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get loader from activity if not set
        if (!::projectLoader.isInitialized) {
            projectLoader = (activity as? MainActivity)?.getProjectLoader() 
                ?: ProjectLoader(requireContext())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)

        recyclerView = view.findViewById(R.id.file_list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val rootNode = try {
            projectLoader.getRootNode()
        } catch (e: Exception) {
            // Create empty root if no project loaded
            FileNode("root", "", null, true)
        }

        recyclerView.adapter = FileNodeAdapter(rootNode) { node ->
            handleNodeClick(node)
        }

        return view
    }

    private fun handleNodeClick(node: FileNode) {
        if (node.isDirectory) {
            // Directory - adapter handles expand/collapse
            return
        }
        
        // File - open in editor
        val editorFragment = EnhancedEditorFragment.newInstance(
            node.path,
            node.uri?.toString() ?: "",
            projectLoader
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, editorFragment)
            .addToBackStack(null)
            .commit()
    }
}