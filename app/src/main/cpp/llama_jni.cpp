// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI wrapper for llama.cpp LLM integration (streaming + multi-turn)
// Updated for llama.cpp 2.x API
// Copyright: CanC-code - CCVO

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

extern "C" {
#include "llama.h"
}

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model* model = nullptr;
static llama_context* ctx = nullptr;
static std::mutex llama_mutex;

// Initialize model from file path
extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaJNI_initModel(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::lock_guard<std::mutex> lock(llama_mutex);

    // Free existing context and model if any
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_free_model(model);
        model = nullptr;
    }

    // Load model
    model = llama_load_model_from_file(path);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load model!");
        return JNI_FALSE;
    }

    // Create context
    llama_context_params params = llama_context_default_params();
    params.n_ctx = 2048;      // context window
    params.n_threads = 4;     // adjust per device
    ctx = llama_new_context(model, params);

    if (!ctx) {
        LOGE("Failed to create context!");
        llama_free_model(model);
        model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded and context created successfully.");
    return JNI_TRUE;
}

// Run prompt and return generated text (single-shot)
extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaJNI_runPrompt(JNIEnv* env, jobject, jstring prompt) {
    const char* cPrompt = env->GetStringUTFChars(prompt, nullptr);
    std::string output;

    std::lock_guard<std::mutex> lock(llama_mutex);
    if (!ctx) {
        env->ReleaseStringUTFChars(prompt, cPrompt);
        return env->NewStringUTF("Model not initialized");
    }

    // Evaluate the prompt
    int eval_res = llama_eval(ctx, cPrompt, strlen(cPrompt), 0);
    if (eval_res != 0) {
        env->ReleaseStringUTFChars(prompt, cPrompt);
        return env->NewStringUTF("Error evaluating prompt");
    }

    // Token generation loop
    int max_tokens = 256;
    for (int i = 0; i < max_tokens; ++i) {
        llama_token token;
        int sample_res = llama_sample_next_token(ctx, &token);
        if (sample_res != 0) break;  // stop on error or end-of-stream
        if (token == LLAMA_TOKEN_EOS) break;

        const char* token_str = llama_token_to_str(ctx, token);
        if (token_str) output += token_str;
    }

    env->ReleaseStringUTFChars(prompt, cPrompt);
    return env->NewStringUTF(output.c_str());
}

// Free model and context
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaJNI_freeModel(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(llama_mutex);
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
        LOGI("Context freed.");
    }
    if (model) {
        llama_free_model(model);
        model = nullptr;
        LOGI("Model freed.");
    }
}