package io.canccode.aca

object LlamaBridge {

    init {
        System.loadLibrary("llama_jni")
    }

    @JvmStatic
    external fun initNative(modelPath: String, nCtx: Int): Boolean

    @JvmStatic
    external fun generateNative(prompt: String, maxTokens: Int): String

    @JvmStatic
    external fun setModelRulesNative(rules: String?)

    @JvmStatic
    external fun setMaxHistoryTurnsNative(turns: Int)

    @JvmStatic
    external fun clearHistoryNative()

    @JvmStatic
    external fun shutdownNative()
}