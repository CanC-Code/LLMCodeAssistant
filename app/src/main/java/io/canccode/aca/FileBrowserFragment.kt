package io.canccode.aca

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import java.io.File

class FileBrowserFragment : Fragment() {

    private var listener: FileSelectionListener? = null
    private lateinit var listView: ListView
    private var currentPath: File = File("/")

    interface FileSelectionListener {
        fun openFileInEditor(file: File)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FileSelectionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement FileSelectionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        listView = view.findViewById(R.id.file_list)
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedFile = listView.adapter.getItem(position) as File
            if (selectedFile.isDirectory) {
                showDirectory(selectedFile)
            } else {
                listener?.openFileInEditor(selectedFile)
            }
        }
        showDirectory(currentPath)
        return view
    }

    private fun showDirectory(dir: File) {
        currentPath = dir
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: emptyList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, files.map { it.name })
        listView.adapter = adapter
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}