// File: app/src/main/cpp/llama_jni.cpp
#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <android/log.h>

extern "C" {
#include "llama.h"
}

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global model context
static llama_context *g_model = nullptr;
static std::mutex g_mutex;

// JNI: load model
extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv *env, jobject thiz, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_mutex);

    // Free old model if any
    if (g_model) {
        llama_free(g_model);
        g_model = nullptr;
    }

    // Set default context params
    llama_context_params params = llama_context_default_params();
    params.n_threads = std::thread::hardware_concurrency();
    params.n_ctx = 512; // context length
    params.seed = 42;   // deterministic
    params.f16 = true;  // float16

    g_model = llama_load_model_from_file(path, params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model from %s", path);
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully: %s", path);
    return JNI_TRUE;
}

// JNI: run prompt
extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv *env, jobject thiz, jstring prompt) {
    const char *c_prompt = env->GetStringUTFChars(prompt, nullptr);
    if (!c_prompt || !g_model) return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(g_mutex);

    std::string output;

    // Tokenize input
    llama_token_data_array tokens_inp = llama_token_data_array_default();
    if (llama_tokenize_with_vocab(g_model->vocab, c_prompt, &tokens_inp, true) != 0) {
        LOGE("Tokenization failed");
        env->ReleaseStringUTFChars(prompt, c_prompt);
        return env->NewStringUTF("");
    }

    // Evaluate model
    if (llama_eval(g_model, tokens_inp.data, tokens_inp.size, 0, g_model->params.n_threads) != 0) {
        LOGE("Model evaluation failed");
        llama_token_data_array_free(&tokens_inp);
        env->ReleaseStringUTFChars(prompt, c_prompt);
        return env->NewStringUTF("");
    }

    // Simple greedy sampling
    int next_token;
    while (true) {
        if (llama_sample_top_p_top_k(g_model, &next_token, 1, 0.9f, 40, 1.0f) != 0) break;
        if (next_token == llama_vocab_eos(g_model->vocab)) break;
        char buf[32];
        int len = llama_token_to_str(g_model->vocab, next_token, buf, sizeof(buf));
        output.append(buf, len);
    }

    llama_token_data_array_free(&tokens_inp);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    return env->NewStringUTF(output.c_str());
}

// JNI: free model
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model) {
        llama_free(g_model);
        g_model = nullptr;
    }
}