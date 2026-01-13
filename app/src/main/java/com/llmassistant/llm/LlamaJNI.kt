package com.llmassistant.llm

object LlamaJNI {

    init {
        System.loadLibrary("llama_jni")
    }

    // -------------------------------------------------
    // Native bindings
    // -------------------------------------------------

    @JvmStatic
    private external fun nativeLoadModel(
        modelPath: String,
        nCtx: Int,
        nThreads: Int
    ): Boolean

    @JvmStatic
    private external fun nativeUnloadModel()

    @JvmStatic
    private external fun nativeTokenize(
        text: String,
        addBos: Boolean
    ): IntArray?

    @JvmStatic
    private external fun nativeTokenToString(
        token: Int
    ): String

    @JvmStatic
    private external fun nativeIsEos(
        token: Int
    ): Boolean

    // -------------------------------------------------
    // Public API used by LLMHandler
    // -------------------------------------------------

    @JvmStatic
    fun initModel(
        modelPath: String,
        contextSize: Int = 4096,
        threads: Int = Runtime.getRuntime().availableProcessors()
    ): Boolean {
        return nativeLoadModel(modelPath, contextSize, threads)
    }

    @JvmStatic
    fun release() {
        nativeUnloadModel()
    }

    /**
     * Minimal placeholder.
     * Real decoding will come later.
     */
    @JvmStatic
    fun runPrompt(prompt: String): String {
        // Tokenize only for now
        val tokens = nativeTokenize(prompt, true) ?: return ""

        val sb = StringBuilder()
        for (t in tokens) {
            if (nativeIsEos(t)) break
            sb.append(nativeTokenToString(t))
        }
        return sb.toString()
    }
}