// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/utils/ModelDownloader.kt
// Author: CCVO
// Purpose: Downloads or updates LLM models into app storage safely and asynchronously

package com.llmassistant.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloader"
    }

    /**
     * Downloads a model file from a URL to local storage.
     * @param modelUrl URL of the model
     * @param fileName Local file name to save under
     * @param onProgress Optional progress callback (0..100)
     * @param onComplete Callback when download finishes (success/failure)
     */
    fun downloadModel(
        modelUrl: String,
        fileName: String,
        onProgress: ((Int) -> Unit)? = null,
        onComplete: (Boolean) -> Unit
    ) {
        Thread {
            try {
                val url = URL(modelUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                val totalSize = connection.contentLength
                val input = connection.inputStream

                val modelDir = File(context.filesDir, "models")
                if (!modelDir.exists()) modelDir.mkdirs()
                val outputFile = File(modelDir, fileName)
                val output = FileOutputStream(outputFile)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                var downloaded = 0

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    val progress = if (totalSize > 0) (downloaded * 100 / totalSize) else 0
                    onProgress?.invoke(progress)
                }

                output.flush()
                output.close()
                input.close()
                connection.disconnect()

                Log.d(TAG, "Model downloaded to ${outputFile.absolutePath}")
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download model: ${e.message}")
                onComplete(false)
            }
        }.start()
    }

    /**
     * Get local model file if exists
     */
    fun getLocalModelFile(fileName: String): File? {
        val file = File(context.filesDir, "models/$fileName")
        return if (file.exists()) file else null
    }
}