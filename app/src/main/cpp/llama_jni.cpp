// File: app/src/main/cpp/llama_jni.cpp
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global singleton context
static llama_context* g_ctx = nullptr;
static std::mutex g_mutex;

extern "C" {

// Initialize model and context
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    // Load model into context
    llama_context_params params;
    params.n_threads = 4;      // Adjust to device
    params.n_batch = 8;
    params.n_ctx = 512;        // Max context length
    params.seed = 42;
    params.f16 = true;

    g_ctx = llama_init_from_file(path, params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_ctx) {
        LOGE("Failed to load model from %s", path);
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

// Run a prompt and return string
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv* env, jobject thiz, jstring prompt) {
    const char* c_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx) {
        env->ReleaseStringUTFChars(prompt, c_prompt);
        return env->NewStringUTF("Error: Model not initialized");
    }

    std::vector<llama_token> tokens;
    if (!llama_tokenize(g_ctx, c_prompt, tokens)) {
        env->ReleaseStringUTFChars(prompt, c_prompt);
        return env->NewStringUTF("Error: Tokenization failed");
    }

    std::string output;
    llama_eval(g_ctx, tokens.data(), tokens.size(), 0, 0);

    // Decode tokens to string
    for (llama_token tok : tokens) {
        char buf[256];
        llama_token_to_str(g_ctx, tok, buf, sizeof(buf));
        output += buf;
    }

    env->ReleaseStringUTFChars(prompt, c_prompt);
    return env->NewStringUTF(output.c_str());
}

// Free model/context
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
        LOGI("Model freed");
    }
}

} // extern "C"