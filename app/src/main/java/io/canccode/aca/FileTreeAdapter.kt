package io.canccode.aca

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * Collapsible file tree adapter.
 * Directories expand/collapse inline.
 */
class FileTreeAdapter(
    private val rootFiles: List<File>,
    private val onFileClick: (File) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.FileViewHolder>() {

    private val visibleNodes = mutableListOf<FileNode>()

    init {
        rootFiles.sortedBy { it.name }.forEach {
            visibleNodes.add(FileNode(it, 0))
        }
    }

    data class FileNode(
        val file: File,
        val level: Int,
        var isExpanded: Boolean = false
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(visibleNodes[position])
    }

    override fun getItemCount(): Int = visibleNodes.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.fileIcon)
        private val name: TextView = itemView.findViewById(R.id.fileName)

        fun bind(node: FileNode) {
            name.text = node.file.name

            itemView.setPadding(
                32 * node.level,
                itemView.paddingTop,
                itemView.paddingRight,
                itemView.paddingBottom
            )

            if (node.file.isDirectory) {
                icon.setImageResource(
                    if (node.isExpanded)
                        android.R.drawable.arrow_down_float
                    else
                        android.R.drawable.arrow_right
                )

                itemView.setOnClickListener {
                    if (node.isExpanded) collapse(node) else expand(node)
                }
            } else {
                icon.setImageResource(android.R.drawable.ic_menu_agenda)
                itemView.setOnClickListener { onFileClick(node.file) }
            }
        }
    }

    private fun expand(node: FileNode) {
        node.isExpanded = true
        val position = visibleNodes.indexOf(node)

        val children = node.file.listFiles()
            ?.sortedBy { it.name }
            ?.map { FileNode(it, node.level + 1) }
            ?: emptyList()

        visibleNodes.addAll(position + 1, children)
        notifyItemRangeInserted(position + 1, children.size)
        notifyItemChanged(position)
    }

    private fun collapse(node: FileNode) {
        node.isExpanded = false
        val position = visibleNodes.indexOf(node)
        val removed = removeChildren(position)
        notifyItemRangeRemoved(position + 1, removed)
        notifyItemChanged(position)
    }

    private fun removeChildren(position: Int): Int {
        val baseLevel = visibleNodes[position].level
        var count = 0

        var i = position + 1
        while (i < visibleNodes.size && visibleNodes[i].level > baseLevel) {
            count++
            i++
        }

        repeat(count) {
            visibleNodes.removeAt(position + 1)
        }

        return count
    }
}