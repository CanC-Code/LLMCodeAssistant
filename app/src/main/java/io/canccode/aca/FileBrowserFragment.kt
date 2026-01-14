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

    private lateinit var fileListRecycler: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        fileListRecycler = view.findViewById(R.id.file_list_recycler)
        fileListRecycler.layoutManager = LinearLayoutManager(requireContext())

        val rootDir = requireContext().filesDir
        val rootFiles = rootDir.listFiles()?.sortedBy { it.name } ?: emptyList()

        fileListRecycler.adapter = FileTreeAdapter(rootFiles) { file ->
            val editorFragment = EditorFragment()
            val bundle = Bundle()
            bundle.putString("filePath", file.absolutePath)
            editorFragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, editorFragment)
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}