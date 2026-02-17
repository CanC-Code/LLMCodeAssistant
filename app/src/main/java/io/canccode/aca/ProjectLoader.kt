package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.BufferedReader

/**
 * Represents a node in the project tree for UI and LLM context.
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
 * Enhanced ProjectLoader with SAF support.
 * Bridges the Android file system to the LLM's context window.
 */
class ProjectLoader(private val context: Context) {

    companion object {
        private const val TAG = "ProjectLoader"
        private const val MAX_FILE_SIZE = 500_000 // 500KB limit to prevent OOM
        private const val MAX_FILES_TO_LOAD = 200 // Limit for initial context
    }

    private val filesMap: MutableMap<String, String> = mutableMapOf()
    private var rootNode: FileNode = FileNode("root", "", null, true)
    private var projectUri: Uri? = null
    private var filesLoaded = 0

    fun loadProject(treeUri: Uri) {
        filesMap.clear()
        projectUri = treeUri
        filesLoaded = 0

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val rootName = getDocumentName(rootUri) ?: "Project"

        rootNode = FileNode(rootName, "", rootUri, true, expanded = true)

        traverseDirectory(treeUri, rootDocId, rootNode, "", 0)
        Log.d(TAG, "Project loaded: $filesLoaded relevant files indexed.")
    }

    private fun traverseDirectory(
        treeUri: Uri,
        parentDocId: String,
        parentNode: FileNode,
        currentPath: String,
        depth: Int
    ) {
        if (depth > 15 || filesLoaded > MAX_FILES_TO_LOAD) return

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

                    // Skip common non-code directories to save memory
                    if (name == "node_modules" || name == ".git" || name == "build") continue

                    val relativePath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val dirNode = FileNode(name, relativePath, docUri, true)
                        parentNode.children.add(dirNode)
                        traverseDirectory(treeUri, docId, dirNode, relativePath, depth + 1)
                    } else {
                        // Only index "code-like" files
                        if (isCodeFile(name)) {
                            filesLoaded++
                            val content = if (size < MAX_FILE_SIZE) loadFileContent(docUri) else "// File too large"
                            filesMap[relativePath] = content
                            parentNode.children.add(FileNode(name, relativePath, docUri, false))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error walking tree", e)
        }
    }

    private fun isCodeFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return listOf("kt", "java", "cpp", "h", "c", "py", "js", "html", "css", "xml", "gradle", "md", "txt").contains(ext)
    }

    private fun loadFileContent(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        } catch (e: Exception) { "" }
    }

    /**
     * Formats the project structure for the LLM.
     * Use this to prep the model: "Here is my project structure: [output]"
     */
    fun getFormattedContext(): String {
        val sb = StringBuilder("Project Structure:\n")
        filesMap.keys.forEach { sb.append("- $it\n") }
        return sb.toString()
    }

    private fun getDocumentName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    fun getFileContent(path: String): String = filesMap[path] ?: ""
    fun getRootNode(): FileNode = rootNode
}
