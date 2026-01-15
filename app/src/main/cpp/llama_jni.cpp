#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::mutex g_mutex;

extern "C" {

JNIEXPORT jboolean JNICALL Java_io_canccode_aca_LlamaBridge_initModel
  (JNIEnv* env, jobject, jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model) {
        LOGI("Model already loaded");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_TRUE;
    }

    llama_model_load_params params{};
    params.n_ctx = 512; // or your default context size
    params.n_gpu_layers = 0;
    params.seed = 42;

    g_model = llama_model_load_from_file(path, &params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    g_ctx = llama_new_context(g_model, &ctx_params);

    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL Java_io_canccode_aca_LlamaBridge_runPrompt
  (JNIEnv* env, jobject, jstring prompt) {

    const char* str_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::string output;

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx) {
        LOGE("Context not initialized");
        env->ReleaseStringUTFChars(prompt, str_prompt);
        return env->NewStringUTF("");
    }

    const llama_vocab* vocab = llama_get_vocab(g_ctx);

    std::vector<llama_token> tokens(1024);
    int n_tokens = llama_tokenize(vocab, str_prompt, true, tokens.data(), tokens.size());

    for (int i = 0; i < n_tokens; ++i) {
        if (llama_eval(g_ctx, &tokens[i], 1, i, 4) != 0) break;
        char buf[128];
        int n = llama_token_to_str(g_ctx, tokens[i], buf, sizeof(buf));
        if (n > 0) output += std::string(buf, n);
    }

    env->ReleaseStringUTFChars(prompt, str_prompt);
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL Java_io_canccode_aca_LlamaBridge_freeModel
  (JNIEnv*, jobject) {

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

} // extern "C"