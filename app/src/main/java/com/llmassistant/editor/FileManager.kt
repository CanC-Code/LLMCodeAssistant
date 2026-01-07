// File: LLMCodeAssistant/app/src/main/java/io/canccode/aca/FileManager.kt
// Author: CCVO
// Purpose: Provides file reading, writing, and utility functions for project files

package io.canccode.aca

import java.io.File
import java.io.IOException

class FileManager {

    // -----------------------------
    // Read file content as list of lines
    // -----------------------------
    fun readFile(file: File): List<String> {
        return try {
            if (file.exists() && file.isFile) {
                file.readLines()
            } else {
                emptyList()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    // -----------------------------
    // Write list of lines to file
    // -----------------------------
    fun writeFile(file: File, lines: List<String>): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(lines.joinToString("\n"))
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    // -----------------------------
    // Append text to file
    // -----------------------------
    fun appendToFile(file: File, text: String): Boolean {
        return try {
            file.appendText(text)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    // -----------------------------
    // List all files in a folder, including hidden
    // -----------------------------
    fun listAllFiles(folder: File): List<File> {
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        return folder.listFiles()?.toList() ?: emptyList()
    }

    // -----------------------------
    // Utility: check if a file is inside a given root folder
    // -----------------------------
    fun isInsideRoot(file: File, root: File): Boolean {
        return try {
            val rootPath = root.canonicalPath
            val filePath = file.canonicalPath
            filePath.startsWith(rootPath)
        } catch (e: Exception) {
            false
        }
    }
}