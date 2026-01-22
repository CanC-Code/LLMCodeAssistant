package io.canccode.aca

object LlamaBridge {

    init {
        System.loadLibrary("llama_jni")
    }

    /**
     * Initialize the LLM model
     * @param modelPath Absolute path to the GGUF model file
     * @param nCtx Context size (recommended: 2048-4096 for Mistral)
     * @return true if successful
     */
    @JvmStatic
    external fun initNative(
        modelPath: String,
        nCtx: Int
    ): Boolean

    /**
     * Generate a response from the model
     * @param prompt User input (will be automatically formatted with Mistral template)
     * @param maxTokens Maximum tokens to generate (recommended: 256-512)
     * @return Generated response text
     */
    @JvmStatic
    external fun generateNative(
        prompt: String,
        maxTokens: Int
    ): String

    /**
     * Clear the chat history
     * Call this to start a fresh conversation
     */
    @JvmStatic
    external fun clearHistoryNative()

    /**
     * Shutdown and free all model resources
     */
    @JvmStatic
    external fun shutdownNative()
}