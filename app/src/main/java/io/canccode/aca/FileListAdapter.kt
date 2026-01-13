package io.canccode.aca.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.canccode.aca.R

class FileListAdapter(
    private val files: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val fileName = files[position]
        holder.fileNameText.text = fileName
        holder.itemView.setOnClickListener { onClick(fileName) }
    }

    override fun getItemCount(): Int = files.size

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileNameText: TextView = view.findViewById(R.id.fileNameText)
    }
}