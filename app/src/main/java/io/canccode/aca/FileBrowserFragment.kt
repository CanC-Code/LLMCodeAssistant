package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * FileBrowserFragment — displays the loaded project tree.
 *
 * FIXES IN THIS REVISION
 * ──────────────────────
 * 1. STALE / EMPTY TREE ON FIRST OPEN
 *    Root cause: getRootNode() was called inside onCreateView() synchronously. If the
 *    project load IO coroutine in MainActivity had not yet finished (race condition on
 *    slow storage), the root node would still be the default empty sentinel and the
 *    list would show nothing.
 *
 *    Fix: observe AppViewModel.projectName. When it emits a non-null value the IO load
 *    has finished and the tree is fully built. We then call refreshTree() to rebuild
 *    the adapter from the now-populated ProjectLoader.
 *
 * 2. NO EMPTY-STATE MESSAGE
 *    When no project is loaded the list was blank with zero feedback.
 *    Fix: show an "emptyState" TextView that explains the user must load a project first.
 *
 * 3. PROJECTLOADER PASSED VIA COMPANION (FRAGMENT FIELD)
 *    Setting a non-serialisable field directly on a Fragment instance via .apply{} is
 *    fine at runtime but breaks after process death (the field becomes null on
 *    re-creation). Fix: retrieve the loader from the Activity via the ProjectProvider
 *    interface in onCreate(), same as EnhancedEditorFragment already does.
 */
class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState:   TextView
    private lateinit var projectLoader: ProjectLoader

    private val viewModel: AppViewModel by activityViewModels()

    companion object {
        /** Factory kept for call-sites that already have a loader reference. */
        fun newInstance(loader: ProjectLoader): FileBrowserFragment {
            return FileBrowserFragment().also { it.projectLoader = loader }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always resolve loader from the Activity so we survive process re-creation.
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
        emptyState   = view.findViewById(R.id.emptyStateText)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initial render
        refreshTree()

        // FIX: re-render whenever the project finishes loading.
        // AppViewModel.projectName becomes non-null only after loadProject() completes
        // and setProjectContext() is called on the main thread.
        viewModel.projectName.observe(viewLifecycleOwner) { refreshTree() }
    }

    private fun refreshTree() {
        val rootNode = try {
            projectLoader.getRootNode()
        } catch (e: Exception) {
            FileNode("root", "", null, true)
        }

        val hasFiles = rootNode.children.isNotEmpty()
        recyclerView.visibility = if (hasFiles) View.VISIBLE else View.GONE
        emptyState.visibility   = if (hasFiles) View.GONE   else View.VISIBLE

        if (hasFiles) {
            recyclerView.adapter = FileNodeAdapter(rootNode) { node -> handleNodeClick(node) }
        }
    }

    private fun handleNodeClick(node: FileNode) {
        if (node.isDirectory) return  // adapter handles expand/collapse

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
