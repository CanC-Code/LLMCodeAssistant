// File: app/src/main/java/io/canccode/aca/LLMHandler.kt
package io.canccode.aca

import android.util.Log
import kotlinx.coroutines.*
import java.io.File

class LLMHandler(private val mainActivity: MainActivity) {

    companion object {
        private const val TAG = "LLMHandler"
    }

    private val handlerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false
    private lateinit var modelFile: File

    /**
     * Initialize the LLM.
     * @param onProgress optional callback with progress 0-100 for download/init.
     */
    fun initialize(onProgress: ((Int) -> Unit)? = null, modelPath: String? = null, nCtx: Int = 512): Deferred<Boolean> {
        return handlerScope.async {
            try {
                // Determine model path
                modelFile = if (modelPath != null) File(modelPath) else File(mainActivity.filesDir, "tinyllama-1.1b-chat.Q4_K_M.gguf")

                // Download if missing
                if (!modelFile.exists()) {
                    mainActivity.runOnUiThread { onProgress?.invoke(0) }
                    downloadModel(modelFile, onProgress)
                } else {
                    Log.i(TAG, "Model already exists: ${modelFile.absolutePath}")
                }

                // Initialize native
                isInitialized = LlamaBridge.initNative(modelFile.absolutePath, nCtx)
                Log.i(TAG, "LLM initialized: $isInitialized")
                isInitialized
            } catch (e: Exception) {
                Log.e(TAG, "LLM initialization error", e)
                isInitialized = false
                false
            } finally {
                mainActivity.runOnUiThread { onProgress?.invoke(100) }
            }
        }
    }

    /**
     * Generate text from a prompt.
     */
    fun generate(prompt: String, maxTokens: Int = 64): String {
        return if (isInitialized) {
            try {
                LlamaBridge.generateNative(prompt, maxTokens)
            } catch (e: Exception) {
                Log.e(TAG, "LLM generation error", e)
                "Error generating text"
            }
        } else {
            Log.e(TAG, "LLM not initialized")
            "LLM not initialized"
        }
    }

    /**
     * Shutdown and free resources.
     */
    fun shutdown() {
        if (isInitialized) {
            try {
                LlamaBridge.shutdownNative()
                isInitialized = false
                Log.i(TAG, "LLM shutdown completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during LLM shutdown", e)
            }
        }
        handlerScope.cancel()
    }

    /**
     * Download the GGUF model.
     */
    private suspend fun downloadModel(destinationFile: File, onProgress: ((Int) -> Unit)? = null) {
        withContext(Dispatchers.IO) {
            val url = java.net.URL(MainActivity.MODEL_URL)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP ${connection.responseCode} downloading model")
            }

            val totalSize = connection.contentLengthLong
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        // Update progress
                        if (totalSize > 0) {
                            val progress = ((downloaded * 100) / totalSize).toInt()
                            mainActivity.runOnUiThread { onProgress?.invoke(progress) }
                        }
                    }
                }
            }

            Log.i(TAG, "Model downloaded to ${destinationFile.absolutePath}")
        }
    }
}