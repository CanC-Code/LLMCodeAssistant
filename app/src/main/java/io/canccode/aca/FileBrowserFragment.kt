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

    // No longer forcing ProjectLoader – use fallback or empty state
    private val projectLoader: ProjectLoader? by lazy(LazyThreadSafetyMode.NONE) {
        // If you later restore it in MainActivity, it can be used here
        (activity as? MainActivity)?.getProjectLoader()
        // or return null / create dummy
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
            // Fallback empty root
            FileNode("Error loading project", "", null, true)
        }

        // Show hint if no project
        if (projectLoader == null) {
            view.findViewById<TextView>(android.R.id.empty)?.apply {
                text = "No project loaded\nLoad a project in Settings first"
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
            // Directory - adapter should handle expand/collapse
            return
        }

        // File clicked → open editor
        val editorFragment = EnhancedEditorFragment.newInstance(
            path = node.path,
            uri = node.uri?.toString() ?: ""
            // loader = projectLoader   ← optional, can be passed if you want
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