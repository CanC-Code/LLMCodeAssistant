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
 * Loads a project using SAF and preserves full directory structure.
 *
 * IMPORTANT:
 *  - Keeps filesMap for backward compatibility
 *  - Introduces a proper tree model for UI
 */
class ProjectLoader(private val context: Context) {

    // Backward-compatible flat map (EditorFragment relies on this)
    private val filesMap: MutableMap<String, String> = mutableMapOf()

    // New: root of the project tree
    private lateinit var rootNode: FileNode

    /**
     * Entry point: load an entire project from a SAF tree URI
     */
    fun loadProject(treeUri: Uri) {
        filesMap.clear()

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

        rootNode = FileNode(
            name = "root",
            path = "",
            uri = rootUri,
            isDirectory = true
        )

        traverseDirectory(
            treeUri = treeUri,
            parentDocId = rootDocId,
            parentNode = rootNode,
            currentPath = ""
        )
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

                val relativePath =
                    if (currentPath.isEmpty()) name else "$currentPath/$name"

                val docUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val dirNode = FileNode(
                        name = name,
                        path = relativePath,
                        uri = docUri,
                        isDirectory = true
                    )

                    parentNode.children.add(dirNode)

                    traverseDirectory(
                        treeUri = treeUri,
                        parentDocId = docId,
                        parentNode = dirNode,
                        currentPath = relativePath
                    )
                } else {
                    val content =
                        context.contentResolver.openInputStream(docUri)
                            ?.bufferedReader()
                            ?.use(BufferedReader::readText)
                            ?: ""

                    // Store flat map (legacy support)
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
     * Legacy API (used by EditorFragment)
     */
    fun getAllFiles(): Map<String, String> = filesMap

    fun getFileContent(path: String): String = filesMap[path] ?: ""

    fun updateFile(path: String, content: String) {
        filesMap[path] = content
        // NOTE: writing back to SAF will be handled later
    }

    /**
     * New API: full project tree
     */
    fun getRootNode(): FileNode = rootNode
}