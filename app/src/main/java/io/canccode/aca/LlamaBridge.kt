package io.canccode.aca

object LlamaBridge {

    init {
        try {
            System.loadLibrary("llama_jni")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("LlamaBridge", "Failed to load native library", e)
        }
    }

    interface GenerateCallback {
        fun onToken(piece: String)
        fun onComplete(fullResponse: String)
        fun onError(error: String)
    }

    external fun initNative(modelPath: String, nCtx: Int): Boolean

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