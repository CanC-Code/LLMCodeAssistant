package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Reads a SAF project tree and builds a flat map of
 *   relative-path -> file-content
 * that can be injected into LLM prompts.
 *
 * Design constraints:
 *  - Skips binary files (images, compiled artifacts, etc.)
 *  - Caps individual file content at MAX_FILE_CHARS to stay within context limits
 *  - Skips hidden files and common noise directories (build/, .git/, node_modules/)
 *  - Total injected context is capped at MAX_TOTAL_CHARS
 */
object ProjectContextBuilder {

    private const val TAG = "ProjectContextBuilder"

    // 6 KB per file — enough for most source files without overwhelming the context
    private const val MAX_FILE_CHARS = 6_000

    // ~60 KB total project context injected into each prompt
    private const val MAX_TOTAL_CHARS = 60_000

    private val SKIP_DIRS = setOf(
        "build", ".git", ".gradle", "node_modules", ".idea",
        "__pycache__", ".DS_Store", "intermediates", "generated"
    )

    private val TEXT_EXTENSIONS = setOf(
        // Code
        "kt", "java", "cpp", "c", "h", "hpp", "py", "js", "ts", "tsx", "jsx",
        "cs", "go", "rs", "swift", "rb", "php", "sh", "bat", "ps1",
        // Config / markup
        "xml", "json", "yaml", "yml", "toml", "ini", "properties", "gradle",
        "pro", "txt", "md", "cmake", "makefile",
        // Web
        "html", "css", "scss", "less",
        // Data
        "csv", "sql"
    )

    /**
     * Synchronously walks the SAF tree rooted at [treeUri] and returns
     * a map of relative paths to file contents, plus the root project name.
     *
     * Call this from a background coroutine (Dispatchers.IO).
     */
    fun build(context: Context, treeUri: Uri): Pair<String, Map<String, String>> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri   = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val rootName  = getDisplayName(context, rootUri) ?: "Project"

        val files = mutableMapOf<String, String>()
        var totalChars = 0

        fun walk(parentDocId: String, pathPrefix: String, depth: Int) {
            if (depth > 12) return
            if (totalChars >= MAX_TOTAL_CHARS) return

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            try {
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext() && totalChars < MAX_TOTAL_CHARS) {
                        val docId = cursor.getString(0)
                        val name  = cursor.getString(1) ?: continue
                        val mime  = cursor.getString(2) ?: ""

                        if (name.startsWith(".") || name in SKIP_DIRS) continue

                        val relativePath = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            walk(docId, relativePath, depth + 1)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext !in TEXT_EXTENSIONS) continue

                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val content = readText(context, docUri) ?: continue
                            val truncated = if (content.length > MAX_FILE_CHARS)
                                content.take(MAX_FILE_CHARS) + "\n...[truncated]"
                            else
                                content

                            files[relativePath] = truncated
                            totalChars += truncated.length
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error walking $pathPrefix", e)
            }
        }

        walk(rootDocId, "", 0)
        Log.i(TAG, "Built project context: ${files.size} files, $totalChars chars")
        return Pair(rootName, files)
    }

    /**
     * Formats the project context as a concise summary block for injection
     * into the system prompt. Lists the file tree first, then file contents.
     */
    fun formatForPrompt(projectName: String, files: Map<String, String>): String {
        if (files.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("=== PROJECT: $projectName ===")
        sb.appendLine("Files in project:")
        files.keys.sorted().forEach { sb.appendLine("  $it") }
        sb.appendLine()
        sb.appendLine("File contents:")

        files.entries
            .sortedBy { it.key }
            .forEach { (path, content) ->
                sb.appendLine("--- $path ---")
                sb.appendLine(content)
                sb.appendLine()
            }

        return sb.toString()
    }

    private fun readText(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $uri", e)
            null
        }
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }
}
