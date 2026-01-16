// File: app/src/main/java/com/llmassistant/utils/ModelDownloader.kt
package com.llmassistant.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModelDownloader(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 8 * 1024
    }

    private val modelDir: File =
        File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Download a model safely and atomically.
     *
     * @param modelUrl URL to GGUF model
     * @param outputName Final file name (e.g. qwen2.5-coder.gguf)
     * @param expectedSha256 REQUIRED hash verification
     * @param onProgress Optional progress callback (0..100)
     */
    suspend fun downloadModel(
        modelUrl: String,
        outputName: String,
        expectedSha256: String,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {

        val finalFile = File(modelDir, outputName)
        val tempFile = File(modelDir, "$outputName.part")

        Log.i(TAG, "Downloading model from $modelUrl")
        Log.i(TAG, "Target: ${finalFile.absolutePath}")

        val connection = URL(modelUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.requestMethod = "GET"
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("HTTP ${connection.responseCode}")
        }

        val totalSize = connection.contentLengthLong
        var downloaded = 0L

        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (totalSize > 0) {
                        onProgress.invoke(((downloaded * 100) / totalSize).toInt())
                    }
                }
            }
        }

        val actualHash = sha256(tempFile)
        if (!actualHash.equals(expectedSha256, ignoreCase = true)) {
            tempFile.delete()
            throw SecurityException(
                "SHA-256 mismatch\nExpected: $expectedSha256\nActual:   $actualHash"
            )
        }

        if (finalFile.exists()) finalFile.delete()
        tempFile.renameTo(finalFile)

        Log.i(TAG, "Model verified and installed")
        finalFile
    }

    /**
     * Returns all installed GGUF models
     */
    fun listModels(): List<File> =
        modelDir.listFiles { f -> f.extension == "gguf" }?.toList() ?: emptyList()

    /**
     * Returns a specific model if installed
     */
    fun getModel(name: String): File? {
        val file = File(modelDir, name)
        return if (file.exists()) file else null
    }

    /**
     * Deletes a model safely
     */
    fun deleteModel(file: File): Boolean {
        if (!file.absolutePath.startsWith(modelDir.absolutePath)) {
            Log.w(TAG, "Refusing to delete outside model dir")
            return false
        }
        return file.delete()
    }

    // -----------------------------
    // Hashing
    // -----------------------------
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}