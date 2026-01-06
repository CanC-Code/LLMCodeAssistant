// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/utils/Tokenizer.kt
// Author: CCVO
// Purpose: Handles tokenization of text for LLM input, counting, and chunking

package com.llmassistant.utils

class Tokenizer {

    /**
     * Simple whitespace-based tokenization.
     * Replace with model-specific tokenizer if needed (BPE, GPT2, etc.)
     */
    fun tokenize(text: String): List<String> {
        return text.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }

    /**
     * Count tokens in a text.
     */
    fun countTokens(text: String): Int {
        return tokenize(text).size
    }

    /**
     * Split text into chunks of max tokens
     * @param text Full text to split
     * @param maxTokens Maximum tokens per chunk
     */
    fun chunkText(text: String, maxTokens: Int): List<String> {
        val tokens = tokenize(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < tokens.size) {
            val end = minOf(start + maxTokens, tokens.size)
            val chunk = tokens.subList(start, end).joinToString(" ")
            chunks.add(chunk)
            start = end
        }
        return chunks
    }
}