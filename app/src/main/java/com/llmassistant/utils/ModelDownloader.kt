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
        private const val DEV_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
    }

    private val modelDir: File =
        File(context.filesDir, "models").apply { mkdirs() }

    suspend fun downloadModel(
        modelUrl: String,
        outputName: String,
        expectedSha256: String,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {

        val finalFile = File(modelDir, outputName)
        val tempFile = File(modelDir, "$outputName.part")

        Log.i(TAG, "Downloading model from $modelUrl")

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
                        onProgress(((downloaded * 100) / totalSize).toInt())
                    }
                }
            }
        }

        if (expectedSha256 != DEV_HASH) {
            val actualHash = sha256(tempFile)
            if (!actualHash.equals(expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                throw SecurityException(
                    "SHA-256 mismatch\nExpected: $expectedSha256\nActual:   $actualHash"
                )
            }
            Log.i(TAG, "SHA-256 verified")
        } else {
            Log.w(TAG, "DEV MODE: SHA-256 verification skipped")
        }

        if (finalFile.exists()) finalFile.delete()
        tempFile.renameTo(finalFile)

        Log.i(TAG, "Model installed at ${finalFile.absolutePath}")
        finalFile
    }

    fun getModel(name: String): File? {
        val file = File(modelDir, name)
        return if (file.exists()) file else null
    }

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