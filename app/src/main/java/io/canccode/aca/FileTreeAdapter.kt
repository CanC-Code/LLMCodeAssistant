package io.canccode.aca

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * Collapsible file-tree adapter for java.io.File hierarchies (local filesystem).
 *
 * IMPORTANT: The inner node class is named [TreeNode] — NOT FileNode — to avoid
 * shadowing the top-level [FileNode] data class defined in ProjectLoader.kt.
 * The original code declared `data class FileNode` inside this class, which caused
 * a compile-time type conflict: the mutable list was typed as the outer FileNode
 * (SAF node: name/path/uri) while the inner constructor expected File/Int — ambiguous
 * and unresolvable by the compiler, preventing the entire project from building.
 */
class FileTreeAdapter(
    private val rootFiles: List<File>,
    private val onFileClick: (File) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.FileViewHolder>() {

    /** Private wrapper for a java.io.File entry with its nesting depth and expand state. */
    private data class TreeNode(
        val file: File,
        val level: Int,
        var isExpanded: Boolean = false
    )

    private val visibleNodes = mutableListOf<TreeNode>()

    init {
        rootFiles.sortedBy { it.name }.forEach {
            visibleNodes.add(TreeNode(it, 0))
        }
    }

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
        private val name: TextView  = itemView.findViewById(R.id.fileName)

        internal fun bind(node: TreeNode) {
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
                        android.R.drawable.ic_media_next
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

    private fun expand(node: TreeNode) {
        node.isExpanded = true
        val position = visibleNodes.indexOf(node)
        val children = node.file.listFiles()
            ?.sortedBy { it.name }
            ?.map { TreeNode(it, node.level + 1) }
            ?: emptyList()
        visibleNodes.addAll(position + 1, children)
        notifyItemRangeInserted(position + 1, children.size)
        notifyItemChanged(position)
    }

    private fun collapse(node: TreeNode) {
        node.isExpanded = false
        val position = visibleNodes.indexOf(node)
        val removed  = removeChildren(position)
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
        repeat(count) { visibleNodes.removeAt(position + 1) }
        return count
    }
}
