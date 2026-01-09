package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ProjectLoader:
 * Loads a designated folder as a "project", reading all supported source files.
 * Supports Kotlin, Java, C/C++, Python, XML, and text files.
 */

class ProjectLoader(private val context: Context) {

    private val TAG = "ProjectLoader"

    // Supported file extensions
    private val extensions = listOf("kt", "java", "cpp", "c", "h", "py", "xml", "txt")

    // Map: relative path -> file contents
    private val filesMap = mutableMapOf<String, String>()

    /**
     * Loads a folder recursively from a Uri (Storage Access Framework)
     */
    fun loadProject(folderUri: Uri) {
        filesMap.clear()
        traverseFolder(folderUri, "")
        Log.i(TAG, "Project loaded: ${filesMap.size} files")
    }

    /**
     * Recursively traverses folderUri and reads files into memory
     */
    private fun traverseFolder(uri: Uri, relativePath: String) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri)
        )

        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val docId = it.getString(0)
                val name = it.getString(1)
                val mime = it.getString(2)

                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                val childRelativePath = if (relativePath.isEmpty()) name else "$relativePath/$name"

                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    traverseFolder(childUri, childRelativePath)
                } else if (isSupportedFile(name)) {
                    val content = readFileContent(childUri)
                    if (content != null) {
                        filesMap[childRelativePath] = content
                    }
                }
            }
        }
    }

    private fun isSupportedFile(fileName: String): Boolean {
        return extensions.any { fileName.endsWith(".$it") }
    }

    private fun readFileContent(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file: $uri", e)
            null
        }
    }

    /**
     * Get the content of a file by relative path
     */
    fun getFileContent(relativePath: String): String? {
        return filesMap[relativePath]
    }

    /**
     * Get all files in the project
     */
    fun getAllFiles(): Map<String, String> {
        return filesMap.toMap()
    }

    /**
     * Get all files with a specific extension
     */
    fun getFilesByExtension(ext: String): Map<String, String> {
        return filesMap.filter { it.key.endsWith(".$ext") }
    }
}