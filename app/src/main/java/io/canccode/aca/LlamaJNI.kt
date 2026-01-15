package io.canccode.aca

object LlamaJNI {

    init {
        System.loadLibrary("llama_jni")
    }

    external fun loadModel(
        path: String,
        nCtx: Int,
        nThreads: Int
    ): Boolean

    external fun generateText(prompt: String): String

    external fun freeModel()
}