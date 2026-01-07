// File: LLMCodeAssistant/app/src/main/java/io/canccode/aca/LLMHandler.kt
// Author: CCVO
// Purpose: Kotlin interface to embedded native LLM (llama.cpp GGUF)
// Notes:
//  - Explicit initialization required
//  - Conversation-aware prompt assembly
//  - Safe context trimming
//  - JNI-backed inference (no network dependency)

package io.canccode.aca

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"

        init {
            // Load JNI bridge produced by CMake
            System.loadLibrary("llama_jni")
        }
    }

    // -------------------------------------------------
    // JNI bindings
    // -------------------------------------------------
    private external fun nativeInitModel(
        modelPath: String,
        threads: Int
    ): Boolean

    private external fun nativeInfer(
        prompt: String,
        maxTokens: Int
    ): String

    private external fun nativeClose()

    // -------------------------------------------------
    // Conversation model
    // -------------------------------------------------
    private enum class Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    private data class Message(
        val role: Role,
        val content: String
    )

    private val conversation = CopyOnWriteArrayList<Message>()

    // Hard safety limit to prevent runaway context growth
    private val maxContextChars = 12_000

    // -------------------------------------------------
    // State
    // -------------------------------------------------
    private var initialized = false
    private var modelFile: File? = null

    // -------------------------------------------------
    // Initialization
    // -------------------------------------------------
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

        if (initialized) {
            conversation.clear()
            conversation.add(
                Message(
                    Role.SYSTEM,
                    "You are a local coding assistant. Be precise, technical, and concise."
                )
            )
        }

        Log.i(TAG, "LLM initialized = $initialized")
        return initialized
    }

    fun isInitialized(): Boolean = initialized

    // -------------------------------------------------
    // Inference (conversation-aware)
    // -------------------------------------------------
    fun infer(
        userInput: String,
        maxTokens: Int = 512
    ): String {
        if (!initialized) {
            return "LLM not initialized"
        }

        conversation.add(
            Message(
                Role.USER,
                userInput.trim()
            )
        )

        trimContextIfNeeded()

        val prompt = buildPrompt()
        Log.d(TAG, "Infer prompt chars=${prompt.length}")

        val reply = nativeInfer(prompt, maxTokens)

        conversation.add(
            Message(
                Role.ASSISTANT,
                reply
            )
        )

        return reply
    }

    // -------------------------------------------------
    // File-aware inference (chunked source analysis)
    // -------------------------------------------------
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
            append("Analyze the following source code:\n\n")
            append(chunk)
            if (userInstruction.isNotBlank()) {
                append("\n\nInstruction:\n")
                append(userInstruction.trim())
            }
        }

        return infer(prompt, maxTokens = 512)
    }

    // -------------------------------------------------
    // Prompt assembly
    // -------------------------------------------------
    private fun buildPrompt(): String {
        val sb = StringBuilder()

        for (msg in conversation) {
            when (msg.role) {
                Role.SYSTEM -> sb.append("[SYSTEM]\n")
                Role.USER -> sb.append("[USER]\n")
                Role.ASSISTANT -> sb.append("[ASSISTANT]\n")
            }
            sb.append(msg.content)
            sb.append("\n\n")
        }

        sb.append("[ASSISTANT]\n")
        return sb.toString()
    }

    // -------------------------------------------------
    // Context trimming
    // -------------------------------------------------
    private fun trimContextIfNeeded() {
        var totalChars = conversation.sumOf { it.content.length }

        // Always preserve SYSTEM message at index 0
        while (totalChars > maxContextChars && conversation.size > 2) {
            val removed = conversation.removeAt(1)
            totalChars -= removed.content.length
        }
    }

    // -------------------------------------------------
    // Conversation reset (model remains loaded)
    // -------------------------------------------------
    fun resetConversation() {
        conversation.clear()
        conversation.add(
            Message(
                Role.SYSTEM,
                "You are a local coding assistant. Be precise, technical, and concise."
            )
        )
    }

    // -------------------------------------------------
    // Shutdown
    // -------------------------------------------------
    @Synchronized
    fun close() {
        if (!initialized) return

        Log.i(TAG, "Shutting down LLM")
        nativeClose()

        initialized = false
        modelFile = null
        conversation.clear()
    }
}