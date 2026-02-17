package io.canccode.aca

import android.util.Log

/**
 * JNI Bridge for llama.cpp optimized for Qwen2.5-Coder.
 * This object manages the lifecycle of the native LLM context.
 */
object LlamaBridge {

    private const val TAG = "LlamaBridge"

    init {
        try {
            System.loadLibrary("llama_jni")
            Log.i(TAG, "Native library llama_jni loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "FATAL: Failed to load native library. Check ABI filters (arm64-v8a required).", e)
        }
    }

    /**
     * Interface for receiving streaming results from the native layer.
     */
    interface GenerateCallback {
        fun onToken(piece: String)
        fun onComplete(fullResponse: String)
        fun onError(error: String)
    }

    /**
     * Initializes the model.
     * @param modelPath Absolute path to the .gguf file. 
     * Note: For SAF, this should be the path to the cached copy in context.cacheDir.
     * @param nCtx Context size (e.g., 2048 for Qwen2.5-Coder-3B).
     */
    fun init(modelPath: String, nCtx: Int): Boolean {
        if (modelPath.isEmpty()) {
            Log.e(TAG, "Model path is empty")
            return false
        }
        return initNative(modelPath, nCtx)
    }

    private external fun initNative(modelPath: String, nCtx: Int): Boolean

    /**
     * Starts inference.
     */
    external fun generateNative(
        prompt: String,
        maxTokens: Int,
        callback: GenerateCallback
    )

    /**
     * Updates the system prompt used in the ChatML template.
     */
    external fun setModelRulesNative(rules: String?)

    /**
     * Clears the internal KV cache and chat history.
     */
    external fun clearHistoryNative()

    /**
     * Frees all native memory.
     */
    external fun shutdownNative()
}
