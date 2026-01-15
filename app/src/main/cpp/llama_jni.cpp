// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: Modern JNI wrapper for llama.cpp integration on Android
// Compatible with newest llama.cpp API
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

static std::mutex g_mutex;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

// ------------------------
// JNI Methods
// ------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path_cstr = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(path_cstr);
    env->ReleaseStringUTFChars(modelPath, path_cstr);

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model || g_ctx) {
        LOGE("Model already loaded");
        return JNI_FALSE;
    }

    // Load model
    g_model = llama_model_load_from_file(path.c_str());
    if (!g_model) {
        LOGE("Failed to load model from path: %s", path.c_str());
        return JNI_FALSE;
    }

    // Set context parameters
    llama_context_params ctx_params;
    llama_default_context_params(&ctx_params);
    ctx_params.n_ctx = 512;          // context size
    ctx_params.n_threads = 4;        // adjust to device cores

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv* env, jobject thiz, jstring prompt) {
    if (!g_ctx) return env->NewStringUTF("");

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string str_prompt(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    std::lock_guard<std::mutex> lock(g_mutex);

    std::vector<llama_token> tokens;

    // Tokenize (new 7-arg signature)
    llama_token* token_buf = nullptr;
    size_t n_token_buf = 0;
    if (llama_tokenize(g_ctx, str_prompt.c_str(), true, &token_buf, &n_token_buf, nullptr, 0) != 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("");
    }

    tokens.assign(token_buf, token_buf + n_token_buf);
    free(token_buf);

    std::string result;
    for (size_t i = 0; i < tokens.size(); ++i) {
        if (llama_eval(g_ctx, &tokens[i], 1, i, 1) != 0) {
            LOGE("Eval failed at token %zu", i);
            break;
        }
        char buf[256];
        int len = llama_token_to_str(g_ctx, tokens[i], buf, sizeof(buf));
        if (len > 0) result.append(buf, len);
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