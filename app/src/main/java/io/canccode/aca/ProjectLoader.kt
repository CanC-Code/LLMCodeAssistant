package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
 */
class ProjectLoader(private val context: Context) {

    // Backward-compatible flat map
    private val filesMap: MutableMap<String, String> = mutableMapOf()

    // Root of the project tree
    private var rootNode: FileNode = FileNode("root", "", null, true)
    
    private var projectUri: Uri? = null

    /**
     * Load an entire project from a SAF tree URI
     */
    fun loadProject(treeUri: Uri) {
        filesMap.clear()
        projectUri = treeUri

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

        traverseDirectory(
            treeUri = treeUri,
            parentDocId = rootDocId,
            parentNode = rootNode,
            currentPath = ""
        )
    }

    /**
     * Get document name from URI
     */
    private fun getDocumentName(uri: Uri): String? {
        return context.contentResolver.query(
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
    }

    /**
     * Recursively walk the document tree and build FileNode hierarchy
     */
    private fun traverseDirectory(
        treeUri: Uri,
        parentDocId: String,
        parentNode: FileNode,
        currentPath: String
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocId
        )

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1)
                val mime = cursor.getString(2)

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
                        currentPath = relativePath
                    )
                } else {
                    // File node
                    val content = try {
                        context.contentResolver.openInputStream(docUri)
                            ?.bufferedReader()
                            ?.use(BufferedReader::readText)
                            ?: ""
                    } catch (e: Exception) {
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
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     * Legacy API (used by existing code)
     */
    fun getAllFiles(): Map<String, String> = filesMap

    fun getFileContent(path: String): String = filesMap[path] ?: ""

    fun updateFile(path: String, content: String) {
        filesMap[path] = content
        // Note: To persist to SAF, call writeFileToUri
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
            false
        }
    }

    /**
     * New API: full project tree
     */
    fun getRootNode(): FileNode = rootNode
    
    fun getProjectUri(): Uri? = projectUri
}