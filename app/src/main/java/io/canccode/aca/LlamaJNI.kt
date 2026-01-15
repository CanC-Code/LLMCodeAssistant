package io.canccode.aca

object LlamaJNI {

    init {
        System.loadLibrary("llama_jni")
    }

    external fun loadModel(
        modelPath: String,
        nCtx: Int,
        nThreads: Int
    ): Boolean

    external fun generateText(prompt: String): String

    external fun freeModel()

    // ------------------------------------------------------------
    // Convenience wrapper (DEFAULTS)
    // ------------------------------------------------------------
    fun loadModelDefault(path: String): Boolean {
        val threads = Runtime.getRuntime()
            .availableProcessors()
            .coerceAtLeast(2)

        return loadModel(path, 2048, threads)
    }
}