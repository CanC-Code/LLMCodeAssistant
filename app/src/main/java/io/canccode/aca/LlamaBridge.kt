// app/src/main/java/io/canccode/aca/LlamaBridge.kt
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
    external fun shutdownNative()
}