package io.canccode.aca

import android.content.Context
import android.util.Log

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"
        private const val DEFAULT_CTX = 2048
    }

    private val modelManager = ModelManager(context)
    private var initialized = false

    suspend fun initialize(onProgress: (Int) -> Unit): Boolean {
        if (initialized) return true

        val ok = modelManager.ensureModel(onProgress)
        if (!ok) return false

        val threads = Runtime.getRuntime()
            .availableProcessors()
            .coerceAtLeast(2)

        initialized = LlamaJNI.loadModel(
            modelManager.getModelPath(),
            DEFAULT_CTX,
            threads
        )

        if (initialized) {
            Log.i(TAG, "LLM initialized")
        } else {
            Log.e(TAG, "LLM failed to initialize")
        }

        return initialized
    }

    fun isInitialized(): Boolean = initialized

    fun infer(prompt: String): String {
        if (!initialized) return "LLM not initialized"
        return LlamaJNI.generateText(prompt)
    }

    fun close() {
        if (!initialized) return
        LlamaJNI.freeModel()
        initialized = false
    }
}