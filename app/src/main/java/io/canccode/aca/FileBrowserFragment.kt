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

    // We'll create a fallback / dummy loader when no real project is loaded
    private val projectLoader: ProjectLoader by lazy {
        // Try to get from activity first (if MainActivity still has it in future)
        (activity as? MainActivity)?.getProjectLoader()
            ?: ProjectLoader(requireContext()) // fallback - creates empty/in-memory state
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No arguments needed anymore
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)

        recyclerView = view.findViewById(R.id.file_list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Get root — fallback to dummy root if loading fails or no project
        val rootNode = try {
            projectLoader.getRootNode()
        } catch (e: Exception) {
            // Fallback: empty root node
            FileNode("No project loaded", "", null, true).also {
                // Optional: show message to user
                view.findViewById<TextView>(android.R.id.empty)?.text = "No project loaded.\nUse Settings → Load Project"
            }
        }

        recyclerView.adapter = FileNodeAdapter(rootNode) { node ->
            handleNodeClick(node)
        }

        return view
    }

    private fun handleNodeClick(node: FileNode) {
        if (node.isDirectory) {
            // Directory - let adapter handle expand/collapse
            return
        }

        // File - open in editor
        val editorFragment = EnhancedEditorFragment.newInstance(
            filePath = node.path,
            fileUriString = node.uri?.toString() ?: "",
            // Pass the same loader reference (or null if you refactor later)
            projectLoader = projectLoader
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, editorFragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        // No arguments needed anymore - simpler instantiation
        fun newInstance(): FileBrowserFragment = FileBrowserFragment()
    }
}