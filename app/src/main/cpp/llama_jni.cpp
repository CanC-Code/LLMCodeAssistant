// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI wrapper for llama.cpp LLM integration (streaming + multi-turn)
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

static llama_context *ctx = nullptr;
static std::mutex llama_mutex;

// Initialize model from file path
extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaJNI_initModel(JNIEnv* env, jobject /*this*/, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::lock_guard<std::mutex> lock(llama_mutex);

    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }

    llama_model_params params;
    params.n_ctx = 2048;    // context size
    params.n_threads = 4;   // adjust for device
    params.seed = 42;

    ctx = llama_init_from_file(path, params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) {
        LOGE("Failed to load model!");
        return JNI_FALSE;
    }
    LOGI("Model loaded successfully.");
    return JNI_TRUE;
}

// Run prompt and return generated text (single-shot)
extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaJNI_runPrompt(JNIEnv* env, jobject /*this*/, jstring prompt) {
    const char* cPrompt = env->GetStringUTFChars(prompt, nullptr);
    std::string output;

    std::lock_guard<std::mutex> lock(llama_mutex);
    if (!ctx) {
        env->ReleaseStringUTFChars(prompt, cPrompt);
        return env->NewStringUTF("Model not initialized");
    }

    llama_eval(ctx, cPrompt, strlen(cPrompt), nullptr, 0); // simple evaluation

    // Simple greedy decode loop for demonstration
    for (int i = 0; i < 256; ++i) {  // max 256 tokens
        llama_token token = llama_token_sample(ctx, nullptr, 0, 0.0f, 1.0f, 1);
        if (token == LLAMA_TOKEN_EOS) break;

        char c = static_cast<char>(token);
        output += c;
    }

    env->ReleaseStringUTFChars(prompt, cPrompt);
    return env->NewStringUTF(output.c_str());
}

// Free model context
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaJNI_freeModel(JNIEnv* env, jobject /*this*/) {
    std::lock_guard<std::mutex> lock(llama_mutex);
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
        LOGI("Model freed.");
    }
}