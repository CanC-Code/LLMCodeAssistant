// File: app/src/main/java/com/llmassistant/llm/LLMHandler.java
// Author: CCVO
// Purpose: Java interface for llama.cpp JNI wrapper
// Copyright: CanC-code - CCVO

package com.llmassistant.llm;

import android.util.Log;

public class LLMHandler {

    private static final String TAG = "LLMHandler";

    static {
        try {
            System.loadLibrary("llama_jni"); // Must match your CMake target
            Log.i(TAG, "LLM JNI library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load LLM JNI library", e);
        }
    }

    // -----------------------------
    // Native methods
    // -----------------------------
    private native boolean nativeInitModel(String modelPath, int threads);
    private native String nativeInfer(String prompt, int maxTokens);
    private native void nativeClose();

    // -----------------------------
    // Public interface
    // -----------------------------
    private boolean initialized = false;

    /**
     * Initialize the LLM with a model file path and number of threads.
     * @param modelPath Absolute path to the model file
     * @param threads Number of threads for inference
     * @return true if initialization succeeded
     */
    public synchronized boolean init(String modelPath, int threads) {
        if (initialized) {
            Log.w(TAG, "LLM already initialized");
            return true;
        }

        boolean success = nativeInitModel(modelPath, threads);
        if (success) {
            initialized = true;
            Log.i(TAG, "LLM initialized with model: " + modelPath);
        } else {
            Log.e(TAG, "Failed to initialize LLM");
        }
        return success;
    }

    /**
     * Generate text based on a prompt.
     * @param prompt The input prompt string
     * @param maxTokens Maximum number of tokens to generate
     * @return Generated text or error message
     */
    public synchronized String infer(String prompt, int maxTokens) {
        if (!initialized) {
            Log.e(TAG, "LLM not initialized");
            return "LLM not initialized";
        }
        return nativeInfer(prompt, maxTokens);
    }

    /**
     * Close the LLM and free resources.
     */
    public synchronized void close() {
        if (!initialized) {
            Log.w(TAG, "LLM already closed or never initialized");
            return;
        }
        nativeClose();
        initialized = false;
        Log.i(TAG, "LLM closed successfully");
    }
}