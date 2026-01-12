// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI wrapper for llama.cpp integration (updated for latest API)
// Copyright: CanC-code - CCVO

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

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static std::mutex g_mutex;

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_Llama_loadModel(JNIEnv *env, jobject /* this */, jstring modelPath, jint nThreads) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model from %s", path);
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_threads = static_cast<int>(nThreads);
    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (!g_ctx) {
        LOGE("Failed to initialize context from model");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_Llama_freeModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    LOGI("Model freed");
}

extern "C" JNIEXPORT jintArray JNICALL
Java_io_canccode_aca_Llama_tokenize(JNIEnv *env, jobject, jstring text, jboolean addBOS) {
    const char *c_text = env->GetStringUTFChars(text, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model) {
        LOGE("Model not loaded");
        env->ReleaseStringUTFChars(text, c_text);
        return nullptr;
    }

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(4096);
    int n_tokens = llama_tokenize(vocab, c_text, tokens.data(), (int)tokens.size(), addBOS, 4, false);

    env->ReleaseStringUTFChars(text, c_text);

    jintArray result = env->NewIntArray(n_tokens);
    env->SetIntArrayRegion(result, 0, n_tokens, reinterpret_cast<jint *>(tokens.data()));
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_Llama_tokenToString(JNIEnv *env, jobject, jint token) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    char buf[256];
    int len = llama_token_to_piece(vocab, token, buf, sizeof(buf));
    if (len < 0) return env->NewStringUTF("");

    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_Llama_isEOS(JNIEnv *, jobject, jint token) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) return JNI_FALSE;

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    return token == llama_vocab_eos(vocab) ? JNI_TRUE : JNI_FALSE;
}