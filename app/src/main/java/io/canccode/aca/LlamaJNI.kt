// File: app/src/main/java/io/canccode/aca/LlamaJNI.kt
// Purpose: Kotlin JNI interface for llama.cpp integration
package io.canccode.aca

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LlamaJNI {
    init {
        System.loadLibrary("llama_jni") // your .so built via CMake
    }

    external fun initModel(modelPath: String): Boolean
    external fun runPrompt(prompt: String): String
    external fun freeModel()

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Run prompt asynchronously and stream output token-by-token
     * onToken callback called for each generated token.
     */
    fun streamPrompt(prompt: String, onToken: (String) -> Unit) {
        scope.launch {
            val fullOutput = runPrompt(prompt)
            fullOutput.forEach { c ->
                onToken(c.toString())
            }
        }
    }

    /**
     * Convenience: Free the model from memory
     */
    fun release() {
        freeModel()
    }
}