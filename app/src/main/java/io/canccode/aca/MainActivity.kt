package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        recyclerView = view.findViewById(R.id.fileRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = FileAdapter(emptyList()) { file ->
            onFileClicked(file)
        }
        recyclerView.adapter = adapter
        loadFiles()
        return view
    }

    private fun loadFiles() {
        // Dummy list for demonstration; replace with actual file loading logic
        val files = listOf("file1.txt", "file2.txt", "file3.txt")
        adapter.updateFiles(files)
    }

    private fun onFileClicked(file: String) {
        Toast.makeText(requireContext(), "Clicked: $file", Toast.LENGTH_SHORT).show()
        // Safe call to MainActivity.switchMode
        (activity as? MainActivity)?.switchMode(EditorFragment())
    }
}