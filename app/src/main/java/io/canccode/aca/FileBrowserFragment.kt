// File: LLMCodeAssistant/app/src/main/java/io/canccode/aca/FileBrowserFragment.kt
package io.canccode.aca

import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.io.File

class FileBrowserFragment : Fragment() {

    companion object {
        private const val ARG_FOLDER_PATH = "folder_path"

        fun newInstance(folderPath: String): FileBrowserFragment =
            FileBrowserFragment().apply {
                arguments = Bundle().apply { putString(ARG_FOLDER_PATH, folderPath) }
            }
    }

    private var currentFolder: File? = null
    private lateinit var listView: ListView
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_FOLDER_PATH)?.let { path ->
            currentFolder = File(path)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        listView = ListView(requireContext())
        currentFolder?.let { loadFiles(it) }
        return listView
    }

    private fun loadFiles(folder: File) {
        if (!folder.exists() || !folder.isDirectory) {
            Toast.makeText(requireContext(), "Folder not found", Toast.LENGTH_SHORT).show()
            return
        }

        files = folder.listFiles()?.sortedBy { it.name } ?: emptyList()
        val fileNames = files.map { if (it.isDirectory) "[${it.name}]" else it.name }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, fileNames)
        listView.adapter = adapter

        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val selected = files[position]
                if (selected.isDirectory) {
                    currentFolder = selected
                    loadFiles(selected)
                } else {
                    (activity as? MainActivity)?.openFileInEditor(selected)
                }
            }
    }
}