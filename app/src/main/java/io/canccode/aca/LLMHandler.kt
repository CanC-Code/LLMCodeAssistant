package io.canccode.aca

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"
        private const val MODEL_NAME = "llama-2-3b.ggmlv3.q4_0.bin"
        private const val MODEL_URL = "https://your-server.com/models/$MODEL_NAME" // replace with your real URL
    }

    private val modelDir: File = File(context.filesDir, "llm_models")
    private val modelFile: File = File(modelDir, MODEL_NAME)
    private var initialized = false

    /**
     * Initialize the LLM: downloads model if missing, loads via JNI
     */
    suspend fun initialize(progressCallback: (Int) -> Unit): Boolean {
        try {
            // Ensure directory exists
            modelDir.mkdirs()

            // Download model if missing
            if (!modelFile.exists()) {
                Log.i(TAG, "Model not found, downloading...")
                downloadModel(modelFile, progressCallback)
                Log.i(TAG, "Model download complete")
            } else {
                Log.i(TAG, "Model found in cache")
            }

            // Load model using JNI wrapper with default nCtx / nThreads
            initialized = LlamaJNI.loadModelDefault(modelFile.absolutePath)

            return initialized
        } catch (e: Exception) {
            Log.e(TAG, "LLM initialization failed", e)
            return false
        }
    }

    /**
     * Download the model with progress callback
     */
    private suspend fun downloadModel(dest: File, progressCallback: (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val url = URL(MODEL_URL)
            dest.parentFile?.mkdirs()
            url.openStream().use { input ->
                dest.outputStream().use { output ->
                    val total = url.openConnection().contentLength
                    var downloaded = 0L
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = (downloaded * 100 / total).toInt()
                        progressCallback(percent.coerceIn(0, 100))
                    }
                }
            }
        }
    }

    /**
     * Generate text using JNI
     */
    fun generateText(prompt: String): String {
        if (!initialized) {
            Log.e(TAG, "LLM not initialized, call initialize() first")
            return ""
        }
        return LlamaJNI.generateText(prompt)
    }

    /**
     * Free model memory
     */
    fun close() {
        if (initialized) {
            LlamaJNI.freeModel()
            initialized = false
            Log.i(TAG, "LLM model freed")
        }
    }
}