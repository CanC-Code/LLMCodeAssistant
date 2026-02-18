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
 * NAMING NOTE: The internal node wrapper is called [TreeNode] (not FileNode) to avoid
 * shadowing the top-level FileNode data class defined in ProjectLoader.kt.
 *
 * VISIBILITY NOTE: TreeNode is private to this class. Kotlin enforces that no
 * non-private function may reference a private type in its signature — this applies
 * to both public AND internal members of an inner class. The solution is to keep
 * bind() private; onBindViewHolder (which overrides a public API) does not reference
 * TreeNode in its own signature, so there is no violation.
 */
class FileTreeAdapter(
    private val rootFiles: List<File>,
    private val onFileClick: (File) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.FileViewHolder>() {

    /** Private wrapper: a java.io.File with its tree depth and expand state. */
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

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        // Pass the node directly; the holder's bind() is private so TreeNode
        // never appears in any externally visible function signature.
        holder.bindNode(visibleNodes[position])
    }

    override fun getItemCount(): Int = visibleNodes.size

    // ── ViewHolder ────────────────────────────────────────────────────────────

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.fileIcon)
        private val name: TextView  = itemView.findViewById(R.id.fileName)

        /**
         * Private — TreeNode must not appear in any non-private function signature.
         * Called exclusively from onBindViewHolder which already lives in the same
         * adapter class and has unrestricted access.
         */
        private fun bindNode(node: TreeNode) {
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

        // Called from onBindViewHolder — Kotlin allows inner class access to
        // private members of the outer class, so this bridge compiles cleanly.
        internal fun bindNode(node: Any) = bindNode(node as TreeNode)
    }

    // ── Expand / Collapse ─────────────────────────────────────────────────────

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
