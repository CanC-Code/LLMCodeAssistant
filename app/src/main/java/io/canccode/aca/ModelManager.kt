package io.canccode.aca

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"

        // CHANGEABLE without touching JNI
        private const val MODEL_FILE_NAME = "llama-2-1.3b-q4_0.gguf"
        private const val MODEL_URL =
            "https://huggingface.co/TheBloke/Llama-2-1.3B-GGUF/resolve/main/llama-2-1.3b-q4_0.gguf"
    }

    private val modelDir = File(context.filesDir, "models")
    private val modelFile = File(modelDir, MODEL_FILE_NAME)

    fun modelExists(): Boolean = modelFile.exists()

    fun getModelPath(): String = modelFile.absolutePath

    suspend fun ensureModel(onProgress: (Int) -> Unit): Boolean {
        if (modelExists()) {
            Log.i(TAG, "Model already present")
            return true
        }

        return downloadModel(onProgress)
    }

    private suspend fun downloadModel(onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {

            try {
                modelDir.mkdirs()

                val url = URL(MODEL_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode != 200) {
                    Log.e(TAG, "HTTP ${connection.responseCode}")
                    return@withContext false
                }

                val total = connection.contentLength
                var downloaded = 0

                connection.inputStream.use { input ->
                    FileOutputStream(modelFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (total > 0) {
                                val percent = (downloaded * 100 / total)
                                onProgress(percent)
                            }
                        }
                    }
                }

                Log.i(TAG, "Model downloaded successfully")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                false
            }
        }
}