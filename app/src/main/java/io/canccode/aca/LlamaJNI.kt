package io.canccode.aca

object LlamaJNI {

    init {
        System.loadLibrary("llama_jni")
    }

    // Load model with context size and threads
    external fun loadModel(path: String, nCtx: Int, nThreads: Int): Boolean

    // Generate text from prompt
    external fun generateText(prompt: String): String

    // Free the model from memory
    external fun freeModel()
}