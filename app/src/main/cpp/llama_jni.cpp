// File: app/src/main/cpp/llama_jni.cpp
// Purpose: Modern JNI bridge for llama.cpp (fully compatible with latest API)

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

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::mutex g_mutex;

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    if (g_ctx) {
        llama_context_free(g_ctx);
        g_ctx = nullptr;
    }

    g_model = llama_model_load(path);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params params = llama_context_default_params();
    g_ctx = llama_init_from_model(g_model, params);

    if (!g_ctx) {
        LOGE("Failed to initialize context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv* env, jobject thiz, jstring prompt) {
    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        LOGE("Context not initialized");
        return env->NewStringUTF("");
    }

    std::vector<llama_token> tokens(4096);
    int n_tokens = llama_tokenize(
        g_ctx,
        prompt_cstr,
        true,       // add_bos
        tokens.data(),
        (int)tokens.size(),
        false,      // allow_special
        true        // allow_extended
    );
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    if (n_tokens <= 0) return env->NewStringUTF("");

    std::string result;
    char buf[256];

    for (int i = 0; i < n_tokens; ++i) {
        int len = llama_token_to_str_with_context(g_ctx, tokens[i], buf, sizeof(buf));
        if (len > 0) {
            result.append(buf, len);
        }
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_context_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    LOGI("Model and context freed");
}