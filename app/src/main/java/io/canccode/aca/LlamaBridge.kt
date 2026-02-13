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
     * @param nCtx Context size (e.g., 4096). Note: Large context increases RAM usage.
     */
    external fun initNative(modelPath: String, nCtx: Int): Boolean

    /**
     * Starts inference.
     * @param prompt The user message.
     * @param maxTokens Maximum new tokens to generate.
     * @param callback The interface to receive stream events.
     */
    external fun generateNative(
        prompt: String,
        maxTokens: Int,
        callback: GenerateCallback
    )

    /**
     * Updates the system prompt used in the ChatML template.
     * Pass null or empty string to reset to default.
     */
    external fun setModelRulesNative(rules: String?)

    /**
     * Sets how many previous conversation turns are included in the prompt context.
     */
    external fun setMaxHistoryTurnsNative(turns: Int)

    /**
     * Clears the internal C++ chat history vector.
     */
    external fun clearHistoryNative()

    /**
     * Frees all native memory (model, context, sampler, and backend).
     * Should be called in Activity.onDestroy().
     */
    external fun shutdownNative()
}
