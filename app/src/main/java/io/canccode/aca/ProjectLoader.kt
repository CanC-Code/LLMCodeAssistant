package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.BufferedReader

/**
 * Represents a node in the project tree.
 * Optimized for hierarchical display and lazy content loading.
 */
data class FileNode(
    val name: String,
    val path: String,
    val uri: Uri?,
    val isDirectory: Boolean,
    val children: MutableList<FileNode> = mutableListOf(),
    var expanded: Boolean = false,
    var isLoaded: Boolean = false // Track if children have been indexed
)

/**
 * Enhanced ProjectLoader with proper SAF support and tree structure.
 * Bridges the gap between Android's virtual file system and the LLM's text context.
 */
class ProjectLoader(private val context: Context) {

    companion object {
        private const val TAG = "ProjectLoader"
        private const val MAX_FILE_SIZE = 512_000 // 500KB limit for LLM context items
        private const val MAX_DEPTH = 15
    }

    // Flat map for quick lookup, values are loaded lazily
    private val filesMap: MutableMap<String, String> = mutableMapOf()

    // Root of the project tree
    private var rootNode: FileNode = FileNode("root", "", null, true)
    private var projectUri: Uri? = null

    /**
     * Initializes the project structure from a SAF tree URI.
     * This performs a shallow load of the root to keep the UI responsive.
     */
    fun loadProject(treeUri: Uri) {
        filesMap.clear()
        projectUri = treeUri

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val rootName = getDocumentName(rootUri) ?: "Project"

        rootNode = FileNode(
            name = rootName,
            path = "",
            uri = rootUri,
            isDirectory = true,
            expanded = true
        )

        Log.i(TAG, "Indexing project: $rootName")
        
        // Initial scan of the top-level directory
        traverseDirectory(treeUri, rootDocId, rootNode, "", 0)
    }

    /**
     * Recursively walk the document tree. 
     * Uses the virtual columns of DocumentsContract to identify files vs directories.
     */
    private fun traverseDirectory(
        treeUri: Uri,
        parentDocId: String,
        parentNode: FileNode,
        currentPath: String,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mime = cursor.getString(2)
                    val size = cursor.getLong(3)

                    // Skip hidden files or common build artifacts to save memory/noise
                    if (name.startsWith(".") || name == "build" || name == "node_modules") continue

                    val relativePath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val dirNode = FileNode(name, relativePath, docUri, true)
                        parentNode.children.add(dirNode)
                        // Deep-crawl can be triggered lazily here or done recursively for small projects
                        traverseDirectory(treeUri, docId, dirNode, relativePath, depth + 1)
                    } else {
                        val fileNode = FileNode(name, relativePath, docUri, false)
                        parentNode.children.add(fileNode)
                        // We store the URI but don't read the content yet (Lazy Loading)
                    }
                }
            }
            parentNode.isLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query directory: $currentPath", e)
        }
    }

    /**
     * Fetches file content on demand. 
     * This is called when the user opens a file or when the LLM needs context.
     */
    fun getFileContent(path: String): String {
        val cachedContent = filesMap[path]
        if (cachedContent != null) return cachedContent

        val node = findNodeByPath(rootNode, path)
        if (node != null && !node.isDirectory && node.uri != null) {
            val content = loadFileContent(node.uri)
            // Cache it to prevent redundant SAF stream overhead
            filesMap[path] = content
            return content
        }
        return ""
    }

    private fun loadFileContent(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Read error for $uri", e)
            "Error: Unable to read file content."
        }
    }

    private fun findNodeByPath(current: FileNode, path: String): FileNode? {
        if (current.path == path) return current
        for (child in current.children) {
            val found = findNodeByPath(child, path)
            if (found != null) return found
        }
        return null
    }

    /**
     * Commits changes back to the SAF provider.
     */
    fun saveFile(path: String, content: String): Boolean {
        val node = findNodeByPath(rootNode, path) ?: return false
        val uri = node.uri ?: return false

        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(content.toByteArray())
                filesMap[path] = content // Update cache
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Write error for $path", e)
            false
        }
    }

    private fun getDocumentName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    fun getRootNode(): FileNode = rootNode
}
