// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/llm/LLMHandler.kt
// Author: CCVO
// Purpose: Kotlin interface to embedded native LLM (llama.cpp GGUF)

package com.llmassistant.llm

import android.content.Context
import android.util.Log
import java.io.File

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"

        init {
            // Loads libllama_jni.so produced by CMake
            System.loadLibrary("llama_jni")
        }
    }

    // -----------------------------
    // Native JNI bindings
    // -----------------------------
    private external fun nativeInitModel(
        modelPath: String,
        threads: Int
    ): Boolean

    private external fun nativeInfer(
        prompt: String,
        maxTokens: Int
    ): String

    private external fun nativeClose()

    // -----------------------------
    // State
    // -----------------------------
    private var initialized = false
    private var modelFile: File? = null

    // -----------------------------
    // Initialization
    // -----------------------------
    @Synchronized
    fun initialize(model: File): Boolean {
        if (initialized) {
            if (modelFile?.absolutePath != model.absolutePath) {
                Log.e(TAG, "LLM already initialized with a different model")
                return false
            }
            return true
        }

        if (!model.exists()) {
            Log.e(TAG, "Model file not found: ${model.absolutePath}")
            return false
        }

        modelFile = model

        val cpuCount = Runtime.getRuntime().availableProcessors()
        val threads = maxOf(1, minOf(4, cpuCount - 1))

        Log.i(TAG, "Initializing LLM")
        Log.i(TAG, "Model: ${model.absolutePath}")
        Log.i(TAG, "Threads: $threads")

        initialized = nativeInitModel(
            model.absolutePath,
            threads
        )

        Log.i(TAG, "LLM initialized = $initialized")
        return initialized
    }

    // -----------------------------
    // Inference (free-form)
    // -----------------------------
    fun infer(
        userInput: String,
        maxTokens: Int = 512,
        contextPrefix: String? = null
    ): String {
        if (!initialized) {
            return "LLM not initialized"
        }

        val prompt = buildString {
            if (!contextPrefix.isNullOrBlank()) {
                append(contextPrefix.trim())
                append("\n\n")
            }
            append(userInput.trim())
        }

        Log.d(TAG, "Infer prompt chars=${prompt.length}")
        return nativeInfer(prompt, maxTokens)
    }

    // -----------------------------
    // File-aware inference (chunked)
    // -----------------------------
    fun inferFileChunk(
        file: File,
        chunkIndex: Int,
        chunkSize: Int = 400,
        userInstruction: String = ""
    ): String {
        if (!initialized) {
            return "LLM not initialized"
        }

        if (!file.exists()) {
            return "File not found: ${file.absolutePath}"
        }

        val lines = file.readLines()
        val start = chunkIndex * chunkSize
        val end = minOf(start + chunkSize, lines.size)

        if (start >= lines.size) {
            return "End of file reached"
        }

        val chunk = lines.subList(start, end).joinToString("\n")

        val prompt = buildString {
            append("You are analyzing the following source code:\n\n")
            append(chunk)
            append("\n\n")
            if (userInstruction.isNotBlank()) {
                append("Instruction:\n")
                append(userInstruction.trim())
            }
        }

        return nativeInfer(prompt, maxTokens = 512)
    }

    // -----------------------------
    // Cleanup
    // -----------------------------
    @Synchronized
    fun close() {
        if (!initialized) return
        Log.i(TAG, "Shutting down LLM")
        nativeClose()
        initialized = false
        modelFile = null
    }
}