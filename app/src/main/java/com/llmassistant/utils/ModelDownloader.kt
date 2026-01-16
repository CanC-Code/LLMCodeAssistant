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
    }

    /**
     * Downloads a model from a URL and saves it as [filename] in app's filesDir.
     * Optionally verifies SHA256 hash if [expectedSha256] is provided.
     */
    suspend fun downloadModel(
        modelUrl: String,
        filename: String,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {

        val destFile = File(context.filesDir, filename)
        Log.i(TAG, "Downloading model from $modelUrl to ${destFile.absolutePath}")

        val url = URL(modelUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("HTTP ${connection.responseCode}")
        }

        val contentLength = connection.contentLength
        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0) {
                        val progress = (totalRead * 100 / contentLength).toInt()
                        onProgress(progress)
                    }
                }
            }
        }

        Log.i(TAG, "Download complete: ${destFile.absolutePath}")

        // Verify SHA256 if expected
        if (!expectedSha256.isNullOrEmpty()) {
            val sha256 = computeSha256(destFile)
            if (!sha256.equals(expectedSha256, ignoreCase = true)) {
                throw RuntimeException("SHA256 mismatch! Expected: $expectedSha256, Got: $sha256")
            }
            Log.i(TAG, "SHA256 verified successfully")
        }

        destFile
    }

    /**
     * Returns the local file if it exists, else null
     */
    fun getModel(filename: String): File? {
        val file = File(context.filesDir, filename)
        return if (file.exists()) file else null
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}