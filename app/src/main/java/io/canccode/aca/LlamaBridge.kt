// File: app/src/main/java/io/canccode/aca/LlamaBridge.kt
package io.canccode.aca

object LlamaBridge {
    init {
        System.loadLibrary("llama_jni")
    }

    external fun initNative(modelPath: String, nCtx: Int): Boolean
    external fun generateNative(prompt: String, maxTokens: Int): String
    external fun shutdownNative()
}