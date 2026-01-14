package io.canccode.aca

import android.content.Context
import com.llmassistant.llm.LLMHandler as JavaLLMHandler
import java.io.File
import android.util.Log

class LLMHandler(context: Context) {

    private val javaHandler = JavaLLMHandler() // wraps JNI

    fun initialize(model: File): Boolean {
        if (!model.exists()) {
            Log.e("LLMHandler", "Model not found: ${model.absolutePath}")
            return false
        }
        return javaHandler.init(model.absolutePath, 4) // 4 threads, for example
    }

    fun infer(prompt: String, maxTokens: Int = 256): String {
        return javaHandler.infer(prompt, maxTokens)
    }

    fun close() {
        javaHandler.close()
    }
}