// File: app/src/main/java/com/llmassistant/llm/LLMHandler.kt
package com.llmassistant.llm

import android.util.Log
import io.canccode.aca.LlamaBridge
import kotlinx.coroutines.*

object LLMHandler {
    private const val TAG = "LLMHandler"
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var initialized = false
        private set

    /**
     * Initialize the LLM with the model path and context size.
     */
    fun init(modelPath: String, nCtx: Int, onResult: (Boolean) -> Unit) {
        ioScope.launch {
            try {
                val ok = LlamaBridge.initNative(modelPath, nCtx)
                initialized = ok
                Log.i(TAG, "LLM initialized: $ok")
                withContext(Dispatchers.Main) { onResult(ok) }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing LLM", e)
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    /**
     * Generate text using the LLM.
     */
    fun generate(prompt: String, maxTokens: Int, onResult: (String) -> Unit) {
        if (!initialized) {
            Log.w(TAG, "LLM not initialized yet")
            onResult("")
            return
        }
        ioScope.launch {
            try {
                val output = LlamaBridge.generateNative(prompt, maxTokens)
                withContext(Dispatchers.Main) { onResult(output) }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating output", e)
                withContext(Dispatchers.Main) { onResult("") }
            }
        }
    }

    /**
     * Shutdown the LLM.
     */
    fun shutdown() {
        ioScope.launch {
            try {
                LlamaBridge.shutdownNative()
                initialized = false
                Log.i(TAG, "LLM shutdown complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down LLM", e)
            }
        }
    }
}