#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global llama context
static llama_context* g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    llama_model_params model_params = llama_model_default_params();
    model_params.n_threads = 4; // Adjust threads for Android

    g_ctx = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_ctx) {
        LOGE("Failed to load llama model");
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv* env, jobject thiz, jstring prompt) {
    if (!g_ctx) return env->NewStringUTF("Model not initialized");

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    if (!promptStr) return env->NewStringUTF("Invalid prompt");

    std::vector<llama_token> tokens(4096);
    int n_tokens = llama_tokenize(
        g_ctx, promptStr, true,
        tokens.data(), tokens.size(),
        nullptr, 0
    );

    std::string result;
    for (int i = 0; i < n_tokens; ++i) {
        char buf[256];
        int len = llama_token_to_str(g_ctx, tokens[i], buf, sizeof(buf));
        if (len > 0) result += std::string(buf, len);
    }

    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv* env, jobject thiz) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
        LOGI("Model freed");
    }
}