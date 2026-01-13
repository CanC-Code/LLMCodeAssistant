package io.canccode.aca

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FileNodeAdapter(
    private val rootNode: FileNode,
    private val onClick: (FileNode) -> Unit
) : RecyclerView.Adapter<FileNodeAdapter.NodeViewHolder>() {

    private val flatList = mutableListOf<FileNode>()

    init {
        rebuildFlatList()
    }

    private fun rebuildFlatList() {
        flatList.clear()
        fun addNodes(node: FileNode, depth: Int) {
            node.children.forEach {
                it.path.let {
                    it
                }
                it.let {}
            }
            node.children.forEach {
                flatList.add(it)
                if (it.isDirectory && it.expanded) {
                    addNodes(it, depth + 1)
                }
            }
        }
        addNodes(rootNode, 0)
    }

    inner class NodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.fileName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return NodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        val node = flatList[position]

        val depth = node.path.count { it == '/' }
        holder.textView.text = node.name
        holder.textView.setPadding(20 * depth, 0, 0, 0)

        // Simple folder/file indicator
        holder.textView.text = if (node.isDirectory) {
            if (node.expanded) "📂 ${node.name}" else "📁 ${node.name}"
        } else {
            "📄 ${node.name}"
        }

        holder.itemView.setOnClickListener {
            onClick(node)
            rebuildFlatList()
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = flatList.size
}