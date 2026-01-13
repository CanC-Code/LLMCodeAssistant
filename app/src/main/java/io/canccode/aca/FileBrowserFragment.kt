package io.canccode.aca.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.canccode.aca.MainActivity
import io.canccode.aca.R
import io.canccode.aca.adapters.FileListAdapter

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)

        recyclerView = view.findViewById(R.id.fileRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val activity = requireActivity() as MainActivity
        adapter = FileListAdapter(activity.getCurrentFileList()) { fileName ->
            val fragment = io.canccode.aca.EditorFragment.newInstance(fileName)
            activity.supportFragmentManager.commit {
                replace(R.id.fragment_container, fragment)
                addToBackStack(null)
            }
        }

        recyclerView.adapter = adapter
        return view
    }
}