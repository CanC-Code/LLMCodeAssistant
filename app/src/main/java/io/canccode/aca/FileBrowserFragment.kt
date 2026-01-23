package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView

    // Correct lazy syntax + nullable + no forced lookup
    private val projectLoader: ProjectLoader? by lazy {
        // If you later add it back to MainActivity → (activity as? MainActivity)?.getProjectLoader()
        // For now: null (no project loading in simplified app)
        null
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
            projectLoader?.getRootNode() ?: FileNode("No project loaded", "", null, true)
        } catch (e: Exception) {
            FileNode("Error loading project", "", null, true)
        }

        // Optional: show message when no project
        if (projectLoader == null) {
            // If your layout has an empty view with id android.R.id.empty
            view.findViewById<TextView>(android.R.id.empty)?.apply {
                text = "No project loaded\nUse Settings to load one (not implemented yet)"
                visibility = View.VISIBLE
            }
        }

        recyclerView.adapter = FileNodeAdapter(rootNode) { node ->
            handleNodeClick(node)
        }

        return view
    }

    private fun handleNodeClick(node: FileNode) {
        if (node.isDirectory) {
            return  // adapter handles expand/collapse
        }

        val editorFragment = EnhancedEditorFragment.newInstance(
            path = node.path,
            uri = node.uri?.toString() ?: ""
            // loader = projectLoader   // optional — can pass if you want
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, editorFragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        fun newInstance(): FileBrowserFragment = FileBrowserFragment()
    }
}