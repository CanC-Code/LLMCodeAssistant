package io.canccode.aca

import android.util.Log

/**
 * JNI Bridge for llama.cpp.
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
     * CHANGED: Renamed from generateNative to generate to match your Fragment calls, 
     * or keep as public so Fragment can see it.
     */
    fun generate(prompt: String, maxTokens: Int, callback: GenerateCallback) {
        generateNative(prompt, maxTokens, callback)
    }

    // Keep this public if your Fragments are explicitly calling "generateNative"
    external fun generateNative(
        prompt: String,
        maxTokens: Int,
        callback: GenerateCallback
    )

    /**
     * Updates the system prompt/rules.
     */
    fun setModelRules(rules: String?) {
        setModelRulesNative(rules)
    }

    external fun setModelRulesNative(rules: String?)

    /**
     * Clears the internal KV cache and chat history.
     */
    fun clearHistory() {
        clearHistoryNative()
    }

    external fun clearHistoryNative()

    /**
     * Frees all native memory.
     */
    fun shutdown() {
        shutdownNative()
    }

    external fun shutdownNative()
}
