package io.canccode.aca

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FileNodeAdapter(
    private val rootNode: FileNode,
    private val onClick: (FileNode) -> Unit
) : RecyclerView.Adapter<FileNodeAdapter.NodeViewHolder>() {

    private val flatList = mutableListOf<Pair<FileNode, Int>>() // Node + depth

    init {
        rebuildFlatList()
    }

    private fun rebuildFlatList() {
        flatList.clear()
        addNodes(rootNode, -1) // Start at -1 so root children are at depth 0
    }

    private fun addNodes(node: FileNode, depth: Int) {
        // Add children of current node
        node.children.sortedWith(compareBy({ !it.isDirectory }, { it.name })).forEach { child ->
            flatList.add(Pair(child, depth + 1))
            
            // If directory is expanded, add its children recursively
            if (child.isDirectory && child.expanded) {
                addNodes(child, depth + 1)
            }
        }
    }

    inner class NodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.fileIcon)
        val textView: TextView = view.findViewById(R.id.fileName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return NodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        val (node, depth) = flatList[position]

        // Indentation based on depth
        val padding = depth * 40
        holder.itemView.setPadding(padding, 8, 8, 8)

        holder.textView.text = node.name

        // Set icon based on type
        if (node.isDirectory) {
            holder.icon.setImageResource(
                if (node.expanded) 
                    android.R.drawable.arrow_down_float
                else 
                    android.R.drawable.ic_menu_more
            )
            
            holder.itemView.setOnClickListener {
                node.expanded = !node.expanded
                onClick(node)
                rebuildFlatList()
                notifyDataSetChanged()
            }
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_edit)
            
            holder.itemView.setOnClickListener {
                onClick(node)
            }
        }
    }

    override fun getItemCount(): Int = flatList.size
}