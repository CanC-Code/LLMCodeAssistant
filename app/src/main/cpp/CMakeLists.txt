#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

// Initialize model
extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_mutex);

    llama_context_params ctx_params{};
    ctx_params.n_ctx = 512;
    ctx_params.n_threads = 4;
    ctx_params.f16_kv = true;

    try {
        g_model = llama_load_model_from_file(path, ctx_params);
        if (!g_model) {
            LOGE("Failed to load llama model from %s", path);
            env->ReleaseStringUTFChars(modelPath, path);
            return JNI_FALSE;
        }

        g_ctx = llama_new_context(g_model, ctx_params);
        if (!g_ctx) {
            LOGE("Failed to create llama context");
            llama_free(g_model);
            g_model = nullptr;
            env->ReleaseStringUTFChars(modelPath, path);
            return JNI_FALSE;
        }
    } catch (...) {
        LOGE("Exception while loading model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

// Run prompt
extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv* env, jobject thiz, jstring prompt) {
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    if (!promptStr) return nullptr;

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx) {
        LOGE("Context not initialized");
        env->ReleaseStringUTFChars(prompt, promptStr);
        return nullptr;
    }

    std::vector<llama_token> tokens;
    llama_token token_buf[1024] = {0};
    int n_tokens = 0;

    // Tokenize
    n_tokens = llama_tokenize(g_ctx, promptStr, true, token_buf, sizeof(token_buf)/sizeof(token_buf[0]));
    if (n_tokens <= 0) {
        LOGE("Tokenization failed");
        env->ReleaseStringUTFChars(prompt, promptStr);
        return nullptr;
    }

    tokens.assign(token_buf, token_buf + n_tokens);

    std::string result;
    for (int i = 0; i < tokens.size(); ++i) {
        if (llama_eval(g_ctx, &tokens[i], 1, i, 1) != 0) break;

        char buf[128] = {0};
        int len = llama_token_to_str(g_ctx, tokens[i], buf, sizeof(buf));
        if (len > 0) result += std::string(buf, len);
    }

    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF(result.c_str());
}

// Free model
extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free(g_model);
        g_model = nullptr;
    }
}