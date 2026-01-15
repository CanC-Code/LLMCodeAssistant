// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI wrapper for latest llama.cpp integration (modern API, Android NDK)
// Note: Compatible with latest llama.cpp (llama_model + llama_context)

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global model and context
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::mutex g_mutex;

// Helper: convert jstring to std::string
static std::string JStringToString(JNIEnv* env, jstring jstr) {
    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string str(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return str;
}

// JNI: Initialize model
extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model || g_ctx) {
        LOGE("Model/context already initialized");
        return JNI_FALSE;
    }

    std::string path = JStringToString(env, modelPath);

    // Load model
    llama_model_params model_params = {};
    model_params.n_ctx = 512;           // context size, adjust as needed
    model_params.n_gpu_layers = 0;      // use CPU only for now
    g_model = llama_load_model_from_file(path.c_str(), model_params);
    if (!g_model) {
        LOGE("Failed to load model from %s", path.c_str());
        return JNI_FALSE;
    }

    // Create context
    llama_context_params ctx_params = {};
    ctx_params.n_threads = 4;           // adjust to device
    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully from %s", path.c_str());
    return JNI_TRUE;
}

// JNI: Run a prompt and return generated text
extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv* env, jobject thiz, jstring prompt) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx) {
        LOGE("Context not initialized");
        return env->NewStringUTF("");
    }

    std::string str_prompt = JStringToString(env, prompt);

    // Tokenize
    std::vector<llama_token> tokens;
    if (llama_tokenize(g_ctx, str_prompt.c_str(), true, tokens) != 0) {
        LOGE("Failed to tokenize prompt");
        return env->NewStringUTF("");
    }

    // Evaluate tokens
    if (llama_eval(g_ctx, tokens.data(), tokens.size(), 0, tokens.size()) != 0) {
        LOGE("Failed to eval tokens");
        return env->NewStringUTF("");
    }

    // Sampling loop (basic, single-token generation example)
    std::string result;
    const int max_tokens = 128;
    for (int i = 0; i < max_tokens; ++i) {
        llama_token next_token;
        if (llama_sample_top_p_top_k(g_ctx, &next_token, 1, 0.9f, 40, 1.0f) != 0) break;
        if (next_token == llama_token_eos()) break;

        char buf[16];
        int len = llama_token_to_str(g_ctx, next_token, buf, sizeof(buf));
        if (len > 0) result.append(buf, len);

        // Feed token back to context
        if (llama_eval(g_ctx, &next_token, 1, tokens.size() + i, 1) != 0) break;
    }

    return env->NewStringUTF(result.c_str());
}

// JNI: Free model and context
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx) {
        llama_free_context(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    LOGI("Model and context freed");
}