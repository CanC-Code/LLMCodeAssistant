// File: app/src/main/java/io/canccode/aca/LLMHandler.kt
package io.canccode.aca

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.Executors

object LLMHandler {
    init {
        System.loadLibrary("llama_jni") // Load JNI library
    }

    private var initialized = false
    private lateinit var modelFile: File
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // JNI functions
    private external fun nativeInit(modelPath: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String

    fun isInitialized(): Boolean = initialized

    /**
     * Initialize model: downloads if needed and calls nativeInit
     */
    fun initialize(context: Context, onProgress: (Float) -> Unit, onComplete: (Boolean) -> Unit) {
        executor.execute {
            try {
                // Set model file path in app storage
                modelFile = File(context.filesDir, "ggml-model.bin")

                // Download model if not exists
                if (!modelFile.exists()) {
                    val url = URL("https://your-server.com/models/ggml-model.bin") // Replace with actual hosted model
                    url.openStream().use { input ->
                        FileOutputStream(modelFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            var total: Long = 0
                            val contentLength = url.openConnection().contentLengthLong

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                total += bytesRead
                                val progress = total.toFloat() / contentLength
                                mainHandler.post { onProgress(progress) }
                            }
                        }
                    }
                }

                // Initialize JNI model
                initialized = nativeInit(modelFile.absolutePath)
                mainHandler.post { onComplete(initialized) }

            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun generate(prompt: String, maxTokens: Int = 128): String {
        if (!initialized) return "LLM not initialized"
        return nativeGenerate(prompt, maxTokens)
    }
}