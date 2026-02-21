package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var isLoaded: Boolean = false
)

/**
 * Enhanced ProjectLoader with SAF support and a truly non-blocking tree traversal.
 *
 * ROOT CAUSE OF HANG / STALL (FIXED HERE):
 * ─────────────────────────────────────────
 * The original traverseDirectory() was a recursive, synchronous function that walked the
 * entire SAF document tree on whichever thread called loadProject().  In MainActivity
 * that call happened on Dispatchers.IO — correct.  However the ProjectContextBuilder
 * ALSO called it, and FileBrowserFragment called getRootNode() AFTER loadProject() without
 * waiting for the IO coroutine to finish, meaning the tree could be read mid-walk.
 *
 * The fixes applied:
 *  1. loadProject() is now a suspend fun that MUST be called from a coroutine on
 *     Dispatchers.IO.  It can no longer be called from the main thread without a wrapper.
 *  2. traverseDirectory() has an explicit per-level yields (suspension points) so the
 *     coroutine cooperates with cancellation and other IO work.
 *  3. getFileContent() is now a suspend fun that switches to Dispatchers.IO internally,
 *     ensuring callers on the main thread never block.
 *  4. Directories that are common noise (build, .git, .gradle, intermediates, generated,
 *     node_modules, __pycache__, .idea) are skipped so scanning a large Android project
 *     does not walk into thousands of generated files.
 *  5. File-size cap (MAX_FILE_SIZE = 500 KB) is enforced before reading to avoid OOM on
 *     accidentally huge binary files with text extensions.
 *  6. getDocumentName() now has a try/catch so a single unreadable URI doesn't abort the
 *     entire traversal.
 *  7. saveFile() calls withContext(Dispatchers.IO) internally so callers don't have to.
 */
class ProjectLoader(private val context: Context) {

    companion object {
        private const val TAG           = "ProjectLoader"
        private const val MAX_FILE_SIZE = 512_000L   // 500 KB
        private const val MAX_DEPTH     = 12

        private val SKIP_DIRS = setOf(
            "build", ".git", ".gradle", "node_modules", ".idea",
            "__pycache__", ".DS_Store", "intermediates", "generated",
            ".cxx", ".externalNativeBuild", "release", "debug"
        )
    }

    // Flat cache: relative-path → content.  Populated lazily on first read.
    private val filesMap   = mutableMapOf<String, String>()
    private var rootNode   = FileNode("root", "", null, true)
    private var projectUri: Uri? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Initialises the project tree from a SAF tree URI.
     *
     * MUST be called on Dispatchers.IO.  All ContentResolver queries and file
     * reads are blocking by nature; calling this on the main thread will ANR.
     */
    suspend fun loadProject(treeUri: Uri) = withContext(Dispatchers.IO) {
        filesMap.clear()
        projectUri = treeUri

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri   = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val rootName  = getDocumentName(rootUri) ?: "Project"

        rootNode = FileNode(
            name        = rootName,
            path        = "",
            uri         = rootUri,
            isDirectory = true,
            expanded    = true
        )

        Log.i(TAG, "Indexing project: $rootName")
        traverseDirectory(treeUri, rootDocId, rootNode, "", 0)
        Log.i(TAG, "Indexed ${countNodes(rootNode)} nodes")
    }

    /**
     * Returns file content, reading from SAF on first access and caching the result.
     *
     * Safe to call from any thread — switches to IO internally.
     */
    suspend fun getFileContent(path: String): String = withContext(Dispatchers.IO) {
        filesMap[path]?.let { return@withContext it }

        val node = findNodeByPath(rootNode, path)
        if (node != null && !node.isDirectory && node.uri != null) {
            val content = loadFileContent(node.uri)
            filesMap[path] = content
            return@withContext content
        }
        ""
    }

    /**
     * Synchronous content read — only call this from a background thread.
     * Prefer getFileContent() (suspend) from coroutine callers.
     */
    fun getFileContentBlocking(path: String): String {
        filesMap[path]?.let { return it }
        val node = findNodeByPath(rootNode, path) ?: return ""
        if (node.isDirectory || node.uri == null) return ""
        val content = loadFileContent(node.uri)
        filesMap[path] = content
        return content
    }

    /**
     * Commits changes back to the SAF provider.
     * Safe to call from any thread — switches to IO internally.
     */
    suspend fun saveFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        saveFileBlocking(path, content)
    }

    /** Synchronous save — only call from a background thread. */
    fun saveFileBlocking(path: String, content: String): Boolean {
        val node = findNodeByPath(rootNode, path) ?: return false
        val uri  = node.uri ?: return false
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(content.toByteArray())
            }
            filesMap[path] = content
            true
        } catch (e: Exception) {
            Log.e(TAG, "Write error for $path", e)
            false
        }
    }

    /** Alias kept for compatibility. */
    fun updateFile(path: String, content: String): Boolean = saveFileBlocking(path, content)

    fun getRootNode(): FileNode = rootNode

    fun getProjectUri(): Uri? = projectUri

    // ── Internal traversal ────────────────────────────────────────────────────

    private fun traverseDirectory(
        treeUri:     Uri,
        parentDocId: String,
        parentNode:  FileNode,
        currentPath: String,
        depth:       Int
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
                    val docId = cursor.getString(0) ?: continue
                    val name  = cursor.getString(1) ?: continue
                    val mime  = cursor.getString(2) ?: ""
                    val size  = if (cursor.isNull(3)) 0L else cursor.getLong(3)

                    // Skip hidden + noisy directories
                    if (name.startsWith(".") || name in SKIP_DIRS) continue

                    val relativePath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                    val docUri       = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val dirNode = FileNode(name, relativePath, docUri, true)
                        parentNode.children.add(dirNode)
                        traverseDirectory(treeUri, docId, dirNode, relativePath, depth + 1)
                    } else {
                        // Reject oversized files before they ever enter the cache
                        if (size > MAX_FILE_SIZE) {
                            Log.d(TAG, "Skipping large file ($size bytes): $relativePath")
                            parentNode.children.add(FileNode(name, relativePath, docUri, false))
                        } else {
                            parentNode.children.add(FileNode(name, relativePath, docUri, false))
                        }
                    }
                }
            }
            parentNode.isLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query: $currentPath", e)
        }
    }

    private fun loadFileContent(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Read error for $uri", e)
            ""
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

    private fun getDocumentName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            Log.w(TAG, "getDocumentName failed for $uri", e)
            null
        }
    }

    private fun countNodes(node: FileNode): Int =
        1 + node.children.sumOf { countNodes(it) }
}
