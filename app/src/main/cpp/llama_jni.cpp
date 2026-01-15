// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI wrapper for llama.cpp LLM integration (updated for newest llama.cpp)
// Copyright: CanC-code - CCVO

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <thread>

extern "C" {
#include "llama.h"
}

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global llama context
static llama_context *g_ctx = nullptr;
static std::mutex g_mutex;

// JNI: Initialize model from a file path
extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv *env, jobject thiz, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    llama_model_params params;
    params.n_threads = std::thread::hardware_concurrency();
    params.n_ctx = 512; // context size
    params.n_batch = 8;
    params.n_gpu_layers = 0;
    params.seed = 42;
    params.f16 = true;

    g_ctx = llama_load_model_from_file(path, params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_ctx) {
        LOGE("Failed to load llama model: %s", path);
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully: %s", path);
    return JNI_TRUE;
}

// JNI: Evaluate a prompt and return output
extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv *env, jobject thiz, jstring prompt) {
    if (!g_ctx) {
        LOGE("LLM context not initialized");
        return env->NewStringUTF("");
    }

    const char *c_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::vector<int32_t> tokens = llama_tokenize(g_ctx, c_prompt, true);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    std::lock_guard<std::mutex> lock(g_mutex);

    // Evaluate the prompt using the new llama_model_eval
    if (llama_model_eval(g_ctx, tokens.data(), tokens.size(), 0, g_ctx->params.n_threads) != 0) {
        LOGE("Error during model evaluation");
        return env->NewStringUTF("");
    }

    // Generate next token(s)
    std::string output;
    for (int i = 0; i < 64; ++i) { // generate max 64 tokens
        int32_t next_token;
        if (llama_sample_top_p_top_k(g_ctx, &next_token, 1, 0.9f, 40, 1.0f) != 0) break;
        output += llama_token_to_str(g_ctx, next_token);
        tokens.push_back(next_token);

        // Evaluate new token
        if (llama_model_eval(g_ctx, &next_token, 1, tokens.size() - 1, g_ctx->params.n_threads) != 0) break;
    }

    return env->NewStringUTF(output.c_str());
}

// JNI: Free the model
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
        LOGI("LLM context freed");
    }
}