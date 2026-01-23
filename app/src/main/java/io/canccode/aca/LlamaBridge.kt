package io.canccode.aca

object LlamaBridge {

    init {
        System.loadLibrary("llama_jni")
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

    external fun shutdownNative()
}