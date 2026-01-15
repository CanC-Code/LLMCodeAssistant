// File: app/src/main/cpp/llama_jni.cpp
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
        llama_free_model(g_model);
        g_model = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Android CPU only

    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model from %s", path);
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    g_ctx = llama_new_context_with_model(g_model, ctx_params);

    if (!g_ctx) {
        LOGE("Failed to create llama context");
        llama_free_model(g_model);
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
        return env->NewStringUTF("Model not initialized");
    }

    std::vector<llama_token> tokens;
    int n_tokens = llama_tokenize(g_ctx, prompt_cstr, true, &tokens);
    if (n_tokens <= 0) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("Failed to tokenize prompt");
    }

    std::string output;
    for (int i = 0; i < n_tokens; ++i) {
        char buf[256];
        int len = llama_token_to_str(g_ctx, tokens[i], buf, sizeof(buf));
        if (len > 0) {
            output += std::string(buf, len);
        }
    }

    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    LOGI("Model freed");
}