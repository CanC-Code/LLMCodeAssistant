package io.canccode.aca

import android.util.Log

object LlamaBridge {

    init {
        try {
            System.loadLibrary("llama_jni")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("LlamaBridge", "Failed to load native library llama_jni", e)
        }
    }

    interface GenerateCallback {
        fun onToken(piece: String)
        fun onComplete(fullResponse: String)
        fun onError(error: String)
    }

    /**
     * Initializes the model using an Android File Descriptor.
     * * @param fd The integer file descriptor from ParcelFileDescriptor.detachFd()
     * @param fileSize The size of the GGUF file in bytes.
     * @param nCtx The context window size (e.g., 2048, 4096).
     * @return true if successful.
     */
    external fun initNative(fd: Int, fileSize: Long, nCtx: Int): Boolean

    external fun generateNative(
        prompt: String,
        maxTokens: Int,
        callback: GenerateCallback
    )

    external fun setModelRulesNative(rules: String?)

    external fun setMaxHistoryTurnsNative(turns: Int)

    external fun clearHistoryNative()

    external fun shutdownNative()
}
