package io.canccode.aca

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.documentfile.provider.DocumentFile
import io.canccode.aca.databinding.ItemFileBinding

class FileListAdapter(
    private val onClick: (DocumentFile) -> Unit
) : ListAdapter<DocumentFile, FileListAdapter.FileViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DocumentFile>() {
            override fun areItemsTheSame(oldItem: DocumentFile, newItem: DocumentFile) =
                oldItem.uri == newItem.uri

            override fun areContentsTheSame(oldItem: DocumentFile, newItem: DocumentFile) =
                oldItem.name == newItem.name
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: DocumentFile) {
            binding.tvFileName.text = file.name
            binding.root.setOnClickListener { onClick(file) }
        }
    }
}