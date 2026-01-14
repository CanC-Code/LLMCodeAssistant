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
    private var currentFileUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_editor, container, false)
        fileListView = root.findViewById(R.id.listFiles)
        editorView = root.findViewById(R.id.editorText)

        editorView.setBackgroundColor(Color.parseColor("#1E1E1E"))
        editorView.setTextColor(Color.parseColor("#D4D4D4"))
        editorView.setTextSize(14f)

        return root
    }

    override fun onResume() {
        super.onResume()
        projectLoader = (activity as MainActivity).projectLoader
        updateFileList()
    }

    private fun updateFileList() {
        val files = projectLoader.getAllFiles().keys.toList()
        fileListView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, files)

        fileListView.setOnItemClickListener { _, _, position, _ ->
            val uri = files[position]
            currentFileUri = uri
            editorView.setText(projectLoader.getFileContent(uri))
        }
    }

    fun saveCurrentFile() {
        currentFileUri?.let {
            projectLoader.updateFile(it, editorView.text.toString())
        }
    }
}