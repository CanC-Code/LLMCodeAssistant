package io.canccode.aca

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.fragment.app.Fragment

class EditorFragment : Fragment() {

    private lateinit var projectLoader: ProjectLoader
    private lateinit var fileListView: ListView
    private lateinit var editorView: EditText

    private var currentFilePath: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_editor, container, false)
        fileListView = root.findViewById(R.id.listFiles)
        editorView = root.findViewById(R.id.editorText)

        // Dark background + readable text
        editorView.setBackgroundColor(Color.parseColor("#1E1E1E"))
        editorView.setTextColor(Color.parseColor("#D4D4D4"))
        editorView.setTextSize(14f)

        return root
    }

    override fun onResume() {
        super.onResume()
        projectLoader = (activity as MainActivity).projectLoader
        refreshFileList()
    }

    /**
     * Refresh the file list if using the old ListView
     * Optional: may be unused if FileBrowserFragment fully replaces it
     */
    private fun refreshFileList() {
        val files = projectLoader.getAllFiles().keys.toList()
        fileListView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, files)

        fileListView.setOnItemClickListener { _, _, position, _ ->
            val path = files[position]
            loadFile(path)
        }
    }

    /**
     * External API to load a file from FileBrowserFragment or elsewhere
     */
    fun loadFile(path: String) {
        val content = projectLoader.getFileContent(path)
        editorView.setText(content)
        currentFilePath = path
    }

    /**
     * Save the current file
     */
    fun saveCurrentFile() {
        currentFilePath?.let {
            projectLoader.updateFile(it, editorView.text.toString())
        }
    }
}