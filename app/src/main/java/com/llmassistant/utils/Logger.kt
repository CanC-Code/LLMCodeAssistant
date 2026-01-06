// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/utils/Logger.kt
// Author: CCVO
// Purpose: Central logging system for debugging and monitoring app, LLM, and file operations

package com.llmassistant.utils

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException

object Logger {

    private const val TAG = "LLMCodeAssistant"
    private var logToFile: Boolean = false
    private var logFile: File? = null

    /**
     * Enable logging to a file in app storage
     */
    fun enableFileLogging(file: File) {
        logFile = file
        logToFile = true
    }

    /**
     * Disable file logging
     */
    fun disableFileLogging() {
        logToFile = false
        logFile = null
    }

    /**
     * Log a debug message
     */
    fun d(message: String) {
        Log.d(TAG, message)
        writeToFile("DEBUG: $message")
    }

    /**
     * Log an error message
     */
    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        writeToFile("ERROR: $message ${throwable?.stackTraceToString() ?: ""}")
    }

    /**
     * Log an info message
     */
    fun i(message: String) {
        Log.i(TAG, message)
        writeToFile("INFO: $message")
    }

    /**
     * Internal: write log message to file if enabled
     */
    private fun writeToFile(message: String) {
        if (!logToFile || logFile == null) return
        try {
            FileWriter(logFile!!, true).use { writer ->
                writer.appendLine(message)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write log to file: ${e.message}")
        }
    }
}