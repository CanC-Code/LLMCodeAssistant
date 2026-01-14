package io.canccode.aca

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.canccode.aca.adapters.FileListAdapter

/**
 * Fragment to display project files for LLM integration
 */
class LLMFragment : Fragment() {

    private lateinit var fileRecyclerView: RecyclerView
    private lateinit var fileAdapter: FileListAdapter
    private lateinit var projectLoader: ProjectLoader

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Instantiate ProjectLoader here with context
        projectLoader = ProjectLoader(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_llm, container, false)

        fileRecyclerView = view.findViewById(R.id.fileRecyclerView)
        fileRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Use legacy API to populate file list
        val fileList = projectLoader.getAllFiles().keys.toList()

        fileAdapter = FileListAdapter(fileList) { fileName ->
            openFile(fileName)
        }
        fileRecyclerView.adapter = fileAdapter

        return view
    }

    private fun openFile(fileName: String) {
        val fragment = EditorFragment.newInstance(fileName)
        parentFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
    }
}