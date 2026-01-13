package io.canccode.aca

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FileListAdapter(
    private var files: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.fileName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file
        holder.itemView.setOnClickListener { onClick(file) }
    }

    override fun getItemCount(): Int = files.size

    // NEW: update the list dynamically
    fun updateFiles(newFiles: List<String>) {
        files = newFiles
        notifyDataSetChanged()
    }
}