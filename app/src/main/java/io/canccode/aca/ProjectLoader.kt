package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.BufferedReader

/**
 * Represents a node in the project tree
 */
data class FileNode(
    val name: String,
    val path: String,
    val uri: Uri?,
    val isDirectory: Boolean,
    val children: MutableList<FileNode> = mutableListOf(),
    var expanded: Boolean = false
)

/**
 * Enhanced ProjectLoader with proper SAF support and tree structure
 * Optimized for large projects with lazy loading
 */
class ProjectLoader(private val context: Context) {

    companion object {
        private const val TAG = "ProjectLoader"
        private const val MAX_FILE_SIZE = 1_000_000 // 1MB limit for in-memory files
        private const val MAX_FILES_TO_LOAD = 500 // Don't load more than 500 files at once
    }

    // Backward-compatible flat map (lazy loaded)
    private val filesMap: MutableMap<String, String> = mutableMapOf()

    // Root of the project tree
    private var rootNode: FileNode = FileNode("root", "", null, true)
    
    private var projectUri: Uri? = null
    private var filesLoaded = 0

    /**
     * Load an entire project from a SAF tree URI
     */
    fun loadProject(treeUri: Uri) {
        filesMap.clear()
        projectUri = treeUri
        filesLoaded = 0

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

        // Get root directory name
        val rootName = getDocumentName(rootUri) ?: "Project"

        rootNode = FileNode(
            name = rootName,
            path = "",
            uri = rootUri,
            isDirectory = true,
            expanded = true
        )

        Log.d(TAG, "Starting project load: $rootName")

        traverseDirectory(
            treeUri = treeUri,
            parentDocId = rootDocId,
            parentNode = rootNode,
            currentPath = "",
            depth = 0
        )
        
        Log.d(TAG, "Project loaded: $filesLoaded files processed")
    }

    /**
     * Get document name from URI
     */
    private fun getDocumentName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting document name", e)
            null
        }
    }

    /**
     * Recursively walk the document tree and build FileNode hierarchy
     * Optimized to prevent crashes on large folders
     */
    private fun traverseDirectory(
        treeUri: Uri,
        parentDocId: String,
        parentNode: FileNode,
        currentPath: String,
        depth: Int
    ) {
        // Limit depth to prevent stack overflow
        if (depth > 20) {
            Log.w(TAG, "Max depth reached at $currentPath")
            return
        }

        // Check if we've loaded too many files
        if (filesLoaded > MAX_FILES_TO_LOAD) {
            Log.w(TAG, "Max files limit reached, stopping traversal")
            return
        }

        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        } catch (e: Exception) {
            Log.e(TAG, "Error building children URI", e)
            return
        }

        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (filesLoaded > MAX_FILES_TO_LOAD) {
                        break
                    }

                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mime = cursor.getString(2)
                    val size = cursor.getLong(3)

                    val relativePath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Directory node
                        val dirNode = FileNode(
                            name = name,
                            path = relativePath,
                            uri = docUri,
                            isDirectory = true
                        )

                        parentNode.children.add(dirNode)

                        // Recursively load children
                        traverseDirectory(
                            treeUri = treeUri,
                            parentDocId = docId,
                            parentNode = dirNode,
                            currentPath = relativePath,
                            depth = depth + 1
                        )
                    } else {
                        filesLoaded++
                        
                        // File node - only load content if small enough
                        val content = if (size < MAX_FILE_SIZE) {
                            loadFileContent(docUri)
                        } else {
                            Log.d(TAG, "Skipping large file: $name (${size / 1024}KB)")
                            ""
                        }

                        // Store in flat map for backward compatibility
                        filesMap[relativePath] = content

                        val fileNode = FileNode(
                            name = name,
                            path = relativePath,
                            uri = docUri,
                            isDirectory = false
                        )

                        parentNode.children.add(fileNode)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying directory: $currentPath", e)
        }
    }

    /**
     * Load file content with error handling
     */
    private fun loadFileContent(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use(BufferedReader::readText)
                ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error loading file content", e)
            ""
        }
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     * Legacy API (used by existing code)
     */
    fun getAllFiles(): Map<String, String> = filesMap

    fun getFileContent(path: String): String {
        // If not in map, try to load it now
        if (!filesMap.containsKey(path)) {
            // Find the node and load its content
            val node = findNodeByPath(rootNode, path)
            if (node != null && !node.isDirectory && node.uri != null) {
                val content = loadFileContent(node.uri)
                filesMap[path] = content
                return content
            }
        }
        return filesMap[path] ?: ""
    }

    private fun findNodeByPath(current: FileNode, path: String): FileNode? {
        if (current.path == path) return current
        for (child in current.children) {
            val found = findNodeByPath(child, path)
            if (found != null) return found
        }
        return null
    }

    fun updateFile(path: String, content: String) {
        filesMap[path] = content
    }

    /**
     * Write content to a file URI
     */
    fun writeFileToUri(uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing file", e)
            false
        }
    }

    /**
     * New API: full project tree
     */
    fun getRootNode(): FileNode = rootNode
    
    fun getProjectUri(): Uri? = projectUri
}