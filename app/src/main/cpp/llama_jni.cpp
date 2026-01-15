// File: app/src/main/cpp/llama_jni.cpp
// Purpose: JNI wrapper for llama.cpp (updated for latest API)
// Author: CCVO

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <thread>

extern "C" {
#include "llama.h"
}

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model *g_model = nullptr;
static std::mutex g_mutex;

// Helper: Convert Java string to C++ string
std::string jstring_to_std(JNIEnv *env, jstring jstr) {
    const char *cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string result(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(JNIEnv *env, jobject thiz, jstring modelPath) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model != nullptr) {
        LOGI("Model already loaded");
        return JNI_TRUE;
    }

    std::string path = jstring_to_std(env, modelPath);
    llama_model_params params = llama_model_default_params();
    params.n_threads = std::thread::hardware_concurrency();
    params.n_ctx = 512;   // context length
    params.seed = 42;     // deterministic
    params.f16 = true;    // use float16

    g_model = llama_model_load_from_file(path.c_str(), params);
    if (!g_model) {
        LOGE("Failed to load model from %s", path.c_str());
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully from %s", path.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(JNIEnv *env, jobject thiz, jstring prompt) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model) {
        LOGE("Model not initialized");
        return env->NewStringUTF("");
    }

    std::string c_prompt = jstring_to_std(env, prompt);

    // Tokenize input
    const int max_tokens = 1024;
    int32_t tokens[max_tokens];
    int32_t n_tokens = 0;

    int ret = llama_tokenize(
        g_model,
        c_prompt.c_str(),
        true,       // add BOS
        tokens,
        max_tokens,
        &n_tokens,
        true        // allow unknown tokens
    );

    if (ret != 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("");
    }

    std::vector<int32_t> output_tokens(tokens, tokens + n_tokens);

    // Generation loop: generate up to 128 new tokens
    for (int i = 0; i < 128; ++i) {
        int32_t next_token = 0;
        llama_token_data_array token_data = {};
        llama_token_data_array_init(&token_data, 1);

        if (llama_eval(g_model, output_tokens.data(), output_tokens.size(), 0, params.n_threads) != 0) {
            LOGE("Evaluation failed");
            break;
        }

        // Sample next token (top-p, top-k)
        if (llama_sample_top_p_top_k(g_model, &next_token, 1, 0.9f, 40, 1.0f) != 0) break;
        output_tokens.push_back(next_token);

        // Stop at EOS token
        if (next_token == llama_token_eos()) break;
    }

    // Convert tokens back to string
    std::string output;
    for (auto t : output_tokens) {
        output += llama_token_to_str(g_model, t);
    }

    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        LOGI("Model freed");
    }
}