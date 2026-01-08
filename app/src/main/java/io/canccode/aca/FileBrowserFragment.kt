package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import java.io.File

class FileBrowserFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewFiles)

        adapter = FileAdapter()
        recyclerView.adapter = adapter

        loadFiles()
        return view
    }

    private fun loadFiles() {
        val filesDir = requireContext().filesDir
        val files = filesDir.listFiles()?.toList() ?: emptyList()
        adapter.submitList(files)
    }
}

class FileAdapter : ListAdapter<File, FileViewHolder>(FileDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val textView: TextView = itemView.findViewById(android.R.id.text1)
    fun bind(file: File) {
        textView.text = file.name
    }
}

class FileDiffCallback : DiffUtil.ItemCallback<File>() {
    override fun areItemsTheSame(oldItem: File, newItem: File) = oldItem.absolutePath == newItem.absolutePath
    override fun areContentsTheSame(oldItem: File, newItem: File) = oldItem == newItem
}