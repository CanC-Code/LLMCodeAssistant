package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import java.io.File

class FileBrowserFragment : Fragment() {

    private lateinit var listView: ListView
    private var currentDir: File = File("/sdcard/") // starting folder

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        listView = ListView(requireContext())
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selected = listView.adapter.getItem(position) as File
            if (selected.isDirectory) {
                currentDir = selected
                updateList()
            } else {
                (activity as? MainActivity)?.openFileInEditor(selected)
            }
        }
        updateList()
        return listView
    }

    private fun updateList() {
        val files = currentDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        listView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, files)
    }
}