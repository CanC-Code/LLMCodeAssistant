package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)

        recyclerView = view.findViewById(R.id.file_list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val rootDir: File = requireContext().filesDir
        val rootFiles = rootDir.listFiles()?.toList() ?: emptyList()

        recyclerView.adapter = FileTreeAdapter(rootFiles) { file ->
            val editorFragment = EditorFragment().apply {
                arguments = Bundle().apply {
                    putString("filePath", file.absolutePath)
                }
            }

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, editorFragment)
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}