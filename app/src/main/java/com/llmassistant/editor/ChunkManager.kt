// File: app/src/main/java/com/llmassistant/editor/ChunkManager.kt
// Author: CCVO
// Purpose: Manages chunking of large files for LLM processing, tracking current chunk, and summaries
// Copyright: CanC-code - CCVO

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
    private val chunkSizeLines: Int = 500  // lines per chunk

    // -----------------------------
    // Load file and prepare chunks
    // -----------------------------
    fun loadFile(file: File) {
        val lines = fileManager.readFile(file)
        fileChunksMap[file.absolutePath] =
            FileChunks(file = file, lines = lines)
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

        return chunks.lines
            .subList(startLine, endLine)
            .joinToString("\n")
    }

    // -----------------------------
    // Move between chunks
    // -----------------------------
    fun moveToNextChunk(file: File) {
        val chunks = fileChunksMap[file.absolutePath] ?: return
        val maxIndex = (chunks.lines.size - 1) / chunkSizeLines
        if (chunks.currentChunkIndex < maxIndex) {
            chunks.currentChunkIndex++
        }
    }

    fun moveToPreviousChunk(file: File) {
        val chunks = fileChunksMap[file.absolutePath] ?: return
        if (chunks.currentChunkIndex > 0) {
            chunks.currentChunkIndex--
        }
    }

    // -----------------------------
    // Query state
    // -----------------------------
    fun currentChunkIndex(file: File): Int {
        return fileChunksMap[file.absolutePath]?.currentChunkIndex ?: 0
    }

    fun resetChunkIndex(file: File) {
        fileChunksMap[file.absolutePath]?.currentChunkIndex = 0
    }

    // -----------------------------
    // Optional: materialize all chunks
    // -----------------------------
    fun getAllChunks(file: File): List<String> {
        val chunks = fileChunksMap[file.absolutePath] ?: run {
            loadFile(file)
            fileChunksMap[file.absolutePath]!!
        }

        val result = mutableListOf<String>()
        var index = 0

        while (true) {
            val start = index * chunkSizeLines
            if (start >= chunks.lines.size) break

            val end = minOf(start + chunkSizeLines, chunks.lines.size)
            result.add(
                chunks.lines.subList(start, end).joinToString("\n")
            )
            index++
        }

        return result
    }
}