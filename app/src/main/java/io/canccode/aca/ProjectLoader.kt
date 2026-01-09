package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedReader

class ProjectLoader(private val context: Context) {

    private val filesMap: MutableMap<String, String> = mutableMapOf()

    /**
     * Entry point: load an entire project from a SAF tree URI
     */
    fun loadProject(treeUri: Uri) {
        filesMap.clear()

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        traverseDirectory(treeUri, rootDocId, "")
    }

    /**
     * Recursively walk the document tree
     */
    private fun traverseDirectory(
        treeUri: Uri,
        parentDocId: String,
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

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    traverseDirectory(treeUri, docId, relativePath)
                } else {
                    val fileUri =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    val content =
                        context.contentResolver.openInputStream(fileUri)
                            ?.bufferedReader()
                            ?.use(BufferedReader::readText)
                            ?: ""

                    filesMap[relativePath] = content
                }
            }
        }
    }

    /**
     * Public API
     */
    fun getAllFiles(): Map<String, String> = filesMap

    fun getFileContent(path: String): String = filesMap[path] ?: ""

    fun updateFile(path: String, content: String) {
        filesMap[path] = content
    }
}