// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/llm/ThreadPoolManager.kt
// Author: CCVO
// Purpose: Manages background threads for LLM inference or other heavy tasks

package com.llmassistant.llm

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class ThreadPoolManager(
    threadCount: Int = Runtime.getRuntime().availableProcessors()
) {

    private val executor = Executors.newFixedThreadPool(threadCount)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Submit a background task.
     * @param task The task to execute in the background.
     */
    fun submit(task: () -> Unit) {
        executor.submit(task)
    }

    /**
     * Submit a task that returns a result, and provide a callback on the UI thread.
     * @param task Task returning a result of type T.
     * @param callback Callback executed on the main thread with the result.
     */
    fun <T> submitWithCallback(task: () -> T, callback: (T) -> Unit) {
        executor.submit {
            val result = task()
            mainHandler.post {
                callback(result)
            }
        }
    }

    /**
     * Shut down the executor when the app or activity is closing.
     */
    fun shutdown() {
        executor.shutdownNow()
    }
}