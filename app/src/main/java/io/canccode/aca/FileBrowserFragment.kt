package io.canccode.aca

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast

class FileBrowserFragment : Fragment() {

    private lateinit var fileList: RecyclerView
    private lateinit var adapter: FileListAdapter
    private val files = mutableListOf<Uri>()

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            files.clear()
            files.add(it)
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        fileList = view.findViewById(R.id.file_list)
        fileList.layoutManager = LinearLayoutManager(requireContext())
        adapter = FileListAdapter(files) { uri ->
            Toast.makeText(requireContext(), "Selected: $uri", Toast.LENGTH_SHORT).show()
        }
        fileList.adapter = adapter

        // Launch SAF picker immediately
        openDocumentLauncher.launch(arrayOf("*/*"))

        return view
    }
}