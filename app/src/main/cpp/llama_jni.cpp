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

extern "C" {

// Initialize the model
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free(g_model);
        g_model = nullptr;
    }

    g_model = llama_load_model(path);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params params = llama_context_default_params();
    g_ctx = llama_new_context(g_model, params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

// Run a prompt
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv* env, jobject thiz, jstring prompt) {
    const char* c_prompt = env->GetStringUTFChars(prompt, nullptr);
    if (!c_prompt || !g_ctx) return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(g_mutex);

    std::vector<llama_token> tokens;
    int n_tokens = llama_tokenize(g_ctx->vocab, c_prompt, tokens);
    std::string output;

    for (int i = 0; i < n_tokens; ++i) {
        char buf[256];
        llama_token_to_str(g_ctx->vocab, tokens[i], buf, sizeof(buf));
        output += buf;
    }

    env->ReleaseStringUTFChars(prompt, c_prompt);
    return env->NewStringUTF(output.c_str());
}

// Free the model
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
    LOGI("Model freed");
}

} // extern "C"