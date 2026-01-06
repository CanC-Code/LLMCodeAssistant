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
    fun initialize(model: File? = null): Boolean {
        if (initialized) return true

        val resolvedModel = model ?: File(context.filesDir, "model.gguf")
        if (!resolvedModel.exists()) {
            Log.e(TAG, "Model file not found: ${resolvedModel.absolutePath}")
            return false
        }

        modelFile = resolvedModel

        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        Log.i(TAG, "Initializing LLM with $threads threads")

        initialized = nativeInitModel(
            resolvedModel.absolutePath,
            threads
        )

        Log.i(TAG, "LLM initialized = $initialized")
        return initialized
    }

    // -----------------------------
    // Inference (free-form, always-on)
    // -----------------------------
    fun infer(
        userInput: String,
        maxTokens: Int = 512,
        contextPrefix: String? = null
    ): String {
        if (!initialized) {
            val ok = initialize()
            if (!ok) return "LLM not initialized"
        }

        val fullPrompt = buildString {
            if (!contextPrefix.isNullOrBlank()) {
                append(contextPrefix)
                append("\n\n")
            }
            append(userInput)
        }

        Log.d(TAG, "Infer prompt length=${fullPrompt.length}")
        return nativeInfer(fullPrompt, maxTokens)
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
                append(userInstruction)
            }
        }

        return infer(prompt, maxTokens = 512)
    }

    // -----------------------------
    // Cleanup
    // -----------------------------
    fun close() {
        if (!initialized) return
        Log.i(TAG, "Shutting down LLM")
        nativeClose()
        initialized = false
    }
}