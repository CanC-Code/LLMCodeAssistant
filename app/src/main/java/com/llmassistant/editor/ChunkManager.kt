// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/editor/ChunkManager.kt
// Author: CCVO
// Purpose: Manages chunking of large files for LLM processing, tracking current chunk, and summaries
// Copyright: CanC-code -CCVO

package com.llmassistant.editor

import java.io.File

class ChunkManager(private val fileManager: FileManager) {

    // -----------------------------
    // Internal data for each file
    // -----------------------------
    data class FileChunks(
        val file: File,
        val lines: List<String>,
        var currentChunkIndex: Int = 0
    )

    private val fileChunksMap = mutableMapOf<String, FileChunks>()
    private val chunkSizeLines: Int = 500  // lines per chunk, configurable

    // -----------------------------
    // Load file and prepare chunks
    // -----------------------------
    fun loadFile(file: File) {
        val lines = fileManager.readFile(file)
        val chunks = FileChunks(file, lines)
        fileChunksMap[file.absolutePath] = chunks
    }

    // -----------------------------
    // SAFE accessor (read-only)
    // -----------------------------
    fun getFileChunks(file: File): FileChunks? {
        return fileChunksMap[file.absolutePath]
    }

    // -----------------------------
    // Get current chunk text
    // -----------------------------
    fun getCurrentChunk(file: File): String {
        val chunks = fileChunksMap[file.absolutePath] ?: run {
            loadFile(file)
            fileChunksMap[file.absolutePath]!!
        }

        val startLine = chunks.currentChunkIndex * chunkSizeLines
        val endLine = minOf(startLine + chunkSizeLines, chunks.lines.size)
        return chunks.lines.subList(startLine, endLine).joinToString("\n")
    }

    // -----------------------------
    // Move to next / previous chunk
    // -----------------------------
    fun moveToNextChunk(file: File) {
        val chunks = fileChunksMap[file.absolutePath] ?: return
        val maxIndex = (chunks.lines.size - 1) / chunkSizeLines
        if (chunks.currentChunkIndex < maxIndex) chunks.currentChunkIndex++
    }

    fun moveToPreviousChunk(file: File) {
        val chunks = fileChunksMap[file.absolutePath] ?: return
        if (chunks.currentChunkIndex > 0) chunks.currentChunkIndex--
    }

    // -----------------------------
    // Reset chunk index to beginning
    // -----------------------------
    fun resetChunkIndex(file: File) {
        fileChunksMap[file.absolutePath]?.currentChunkIndex = 0
    }

    // -----------------------------
    // Optional: Summarize all chunks
    // -----------------------------
    fun getAllChunks(file: File): List<String> {
        val chunks = fileChunksMap[file.absolutePath] ?: run {
            loadFile(file)
            fileChunksMap[file.absolutePath]!!
        }

        val chunkList = mutableListOf<String>()
        var index = 0
        while (true) {
            val startLine = index * chunkSizeLines
            if (startLine >= chunks.lines.size) break
            val endLine = minOf(startLine + chunkSizeLines, chunks.lines.size)
            chunkList.add(
                chunks.lines.subList(startLine, endLine).joinToString("\n")
            )
            index++
        }
        return chunkList
    }
}