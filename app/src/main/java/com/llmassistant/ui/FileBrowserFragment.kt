// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/FileBrowserFragment.kt
// Author: CCVO
// Purpose: Browse project folder files/folders, including hidden, and open files in editor

package com.llmassistant.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.llmassistant.R
import java.io.File

class FileBrowserFragment : Fragment() {

    companion object {
        private const val ARG_FOLDER_PATH = "folder_path"

        fun newInstance(folderPath: String): FileBrowserFragment {
            val fragment = FileBrowserFragment()
            val args = Bundle()
            args.putString(ARG_FOLDER_PATH, folderPath)
            fragment.arguments = args
            return fragment
        }
    }

    private var projectFolderPath: String? = null
    private lateinit var listView: ListView
    private lateinit var adapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectFolderPath = arguments?.getString(ARG_FOLDER_PATH)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_file_browser, container, false)
        listView = root.findViewById(R.id.file_list_view)
        adapter = FileAdapter { file -> onFileClicked(file) }
        listView.adapter = adapter

        projectFolderPath?.let {
            val folder = File(it)
            if (folder.exists() && folder.isDirectory) {
                loadFolder(folder)
            } else {
                Toast.makeText(context, "Project folder invalid", Toast.LENGTH_LONG).show()
            }
        }

        return root
    }

    private fun loadFolder(folder: File) {
        val files = folder.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        adapter.submitList(files)
    }

    private fun onFileClicked(file: File) {
        val mainActivity = activity as? MainActivity ?: return

        val isOutside = !mainActivity.checkFileWithinProject(file)
        mainActivity.indicateOutsideProject(isOutside, listView)

        if (file.isDirectory) {
            loadFolder(file)
        } else {
            mainActivity.openFileInEditor(file)
        }
    }

    // -----------------------------
    // Adapter for ListView
    // -----------------------------
    private class FileAdapter(val clickCallback: (File) -> Unit) : BaseAdapter() {
        private val items = mutableListOf<File>()

        fun submitList(list: List<File>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val file = items[position]
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            val text = view.findViewById<TextView>(android.R.id.text1)
            text.text = file.name
            text.setTextColor(if (file.isDirectory) Color.BLUE else Color.BLACK)
            view.setOnClickListener { clickCallback(file) }
            return view
        }
    }
}