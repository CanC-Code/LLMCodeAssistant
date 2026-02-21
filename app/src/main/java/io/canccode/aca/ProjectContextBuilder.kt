package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.WorkerThread

/**
 * Reads a SAF project tree and builds a flat map of
 *   relative-path -> file-content
 * for injection into LLM prompts.
 *
 * FIXES IN THIS REVISION
 * ──────────────────────
 * 1. @WorkerThread annotation added to build() — it performs blocking IO and must
 *    never be called from the main thread. The annotation causes lint to flag any
 *    violation at compile time.
 *
 * 2. Added "kts" to TEXT_EXTENSIONS so Kotlin build scripts (build.gradle.kts,
 *    settings.gradle.kts) are indexed.
 *
 * 3. cursor.getString(1) could return null for corrupted documents — added ?.
 *    continue guards to avoid NPE inside the walk lambda.
 *
 * 4. readText() timeout protection: very large files could stall the IO thread
 *    indefinitely. Added a size check using COLUMN_SIZE before opening the stream.
 */
object ProjectContextBuilder {

    private const val TAG = "ProjectContextBuilder"

    // 6 KB per file — enough for most source files
    private const val MAX_FILE_CHARS  = 6_000

    // ~60 KB total project context
    private const val MAX_TOTAL_CHARS = 60_000

    // 500 KB hard cap on individual file size before we even open the stream
    private const val MAX_FILE_BYTES  = 500_000L

    private val SKIP_DIRS = setOf(
        "build", ".git", ".gradle", "node_modules", ".idea",
        "__pycache__", ".DS_Store", "intermediates", "generated",
        ".cxx", ".externalNativeBuild"
    )

    private val TEXT_EXTENSIONS = setOf(
        // Code
        "kt", "kts", "java", "cpp", "c", "h", "hpp", "py", "js", "ts", "tsx", "jsx",
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
     * MUST be called from a background thread (Dispatchers.IO).
     */
    @WorkerThread
    fun build(context: Context, treeUri: Uri): Pair<String, Map<String, String>> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri   = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val rootName  = getDisplayName(context, rootUri) ?: "Project"

        val files      = mutableMapOf<String, String>()
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
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE
                    ),
                    null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext() && totalChars < MAX_TOTAL_CHARS) {
                        val docId = cursor.getString(0) ?: continue
                        // FIX: null guard on display name
                        val name  = cursor.getString(1) ?: continue
                        val mime  = cursor.getString(2) ?: ""
                        val size  = if (cursor.isNull(3)) 0L else cursor.getLong(3)

                        if (name.startsWith(".") || name in SKIP_DIRS) continue

                        val relativePath = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            walk(docId, relativePath, depth + 1)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext !in TEXT_EXTENSIONS) continue

                            // FIX: skip oversized files before opening stream
                            if (size > MAX_FILE_BYTES) {
                                Log.d(TAG, "Skipping large file ($size bytes): $relativePath")
                                continue
                            }

                            val docUri  = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val content = readText(context, docUri) ?: continue
                            val truncated = if (content.length > MAX_FILE_CHARS)
                                content.take(MAX_FILE_CHARS) + "\n...[truncated]"
                            else
                                content

                            files[relativePath]  = truncated
                            totalChars          += truncated.length
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
     * Formats the project context as a concise summary block for system prompt injection.
     */
    fun formatForPrompt(projectName: String, files: Map<String, String>): String {
        if (files.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("=== PROJECT: $projectName ===")
        sb.appendLine("Files in project:")
        files.keys.sorted().forEach { sb.appendLine("  $it") }
        sb.appendLine()
        sb.appendLine("File contents:")

        files.entries.sortedBy { it.key }.forEach { (path, content) ->
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
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            Log.w(TAG, "getDisplayName failed", e)
            null
        }
    }
}
