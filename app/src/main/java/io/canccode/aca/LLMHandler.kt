package io.canccode.aca

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class LLMHandler(private val context: Context) {

    companion object {
        private const val TAG = "LLMHandler"
    }

    // -------------------------------------------------
    // Conversation model
    // -------------------------------------------------
    private enum class Role { SYSTEM, USER, ASSISTANT }
    private data class Message(val role: Role, val content: String)
    private val conversation = CopyOnWriteArrayList<Message>()
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

        Log.i(TAG, "Initializing LLM")
        Log.i(TAG, "Model: ${model.absolutePath}")

        initialized = LlamaJNI.loadModel(model.absolutePath)

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
    fun infer(userInput: String): String {
        if (!initialized) return "LLM not initialized"

        conversation.add(Message(Role.USER, userInput.trim()))
        trimContextIfNeeded()

        val prompt = buildPrompt()
        Log.d(TAG, "Infer prompt chars=${prompt.length}")

        val reply = LlamaJNI.generateText(prompt)
        conversation.add(Message(Role.ASSISTANT, reply))

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
        if (!initialized) return "LLM not initialized"
        if (!file.exists()) return "File not found: ${file.absolutePath}"

        val lines = file.readLines()
        val start = chunkIndex * chunkSize
        val end = minOf(start + chunkSize, lines.size)

        if (start >= lines.size) return "End of file reached"

        val chunk = lines.subList(start, end).joinToString("\n")
        val prompt = buildString {
            append("Analyze the following source code:\n\n")
            append(chunk)
            if (userInstruction.isNotBlank()) {
                append("\n\nInstruction:\n")
                append(userInstruction.trim())
            }
        }

        return infer(prompt)
    }

    // -------------------------------------------------
    // Prompt assembly
    // -------------------------------------------------
    private fun buildPrompt(): String {
        val sb = StringBuilder()
        for (msg in conversation) {
            sb.append(
                when (msg.role) {
                    Role.SYSTEM -> "[SYSTEM]\n"
                    Role.USER -> "[USER]\n"
                    Role.ASSISTANT -> "[ASSISTANT]\n"
                }
            )
            sb.append(msg.content).append("\n\n")
        }
        sb.append("[ASSISTANT]\n")
        return sb.toString()
    }

    // -------------------------------------------------
    // Context trimming
    // -------------------------------------------------
    private fun trimContextIfNeeded() {
        var totalChars = conversation.sumOf { it.content.length }
        while (totalChars > maxContextChars && conversation.size > 2) {
            val removed = conversation.removeAt(1)
            totalChars -= removed.content.length
        }
    }

    // -------------------------------------------------
    // Conversation reset
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
        LlamaJNI.freeModel()

        initialized = false
        modelFile = null
        conversation.clear()
    }
}