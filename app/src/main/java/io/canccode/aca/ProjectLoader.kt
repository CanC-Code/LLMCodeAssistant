package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedReader
import java.io.InputStreamReader

class ProjectLoader(private val context: Context) {

    private val filesMap: MutableMap<String, String> = mutableMapOf()

    // Load all files recursively from SAF folder
    fun loadProject(folderUri: Uri) {
        filesMap.clear()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val cursor = context.contentResolver.query(childrenUri, arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val docId = it.getString(1)
                val mime = it.getString(2)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    // Recursively load subfolder
                    loadProject(childUri)
                } else {
                    // Read file content
                    val content = context.contentResolver.openInputStream(childUri)?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                    filesMap[childUri.toString()] = content
                }
            }
        }
    }

    fun getAllFiles(): Map<String, String> = filesMap

    fun getFileContent(uri: String): String = filesMap[uri] ?: ""

    fun updateFile(uri: String, content: String) {
        filesMap[uri] = content
    }
}