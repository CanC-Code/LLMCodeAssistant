// File: app/src/main/java/io/canccode/aca/LLMHandler.kt
package io.canccode.aca

import android.content.Context
import com.llmassistant.llm.LLMHandler as JavaLLMHandler

/**
 * Kotlin wrapper for the Java LLMHandler (JNI) class.
 * Provides a clean interface for the Kotlin codebase.
 */
class LLMHandler(context: Context) {

    private val javaHandler = JavaLLMHandler()

    /**
     * Initialize the LLM model
     * @param modelPath Absolute path to model file
     * @param threads Number of threads
     * @return true if successful
     */
    fun init(modelPath: String, threads: Int): Boolean {
        return javaHandler.init(modelPath, threads)
    }

    /**
     * Run inference on a prompt
     * @param prompt Input string
     * @param maxTokens Maximum tokens to generate
     * @return Generated string
     */
    fun infer(prompt: String, maxTokens: Int): String {
        return javaHandler.infer(prompt, maxTokens)
    }

    /**
     * Close the model and free resources
     */
    fun close() {
        javaHandler.close()
    }
}