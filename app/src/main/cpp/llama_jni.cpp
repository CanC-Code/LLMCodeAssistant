// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI bridge between Kotlin and llama.cpp GGUF inference

#include <jni.h>
#include <string>
#include <android/log.h>
#include "llama/llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_llmassistant_llm_LLMHandler_initModel(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jint threads
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init(false);

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_load_model_from_file(path, model_params);

    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_threads = threads;
    ctx_params.n_ctx = 4096;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_llmassistant_llm_LLMHandler_infer(
        JNIEnv* env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens
) {
    if (!g_ctx) {
        return env->NewStringUTF("Model not initialized");
    }

    const char* input = env->GetStringUTFChars(prompt, nullptr);

    llama_reset_timings(g_ctx);

    std::string output;

    llama_token tokens[4096];
    int n_tokens = llama_tokenize(
            g_model,
            input,
            strlen(input),
            tokens,
            4096,
            true,
            true
    );

    llama_eval(g_ctx, tokens, n_tokens, 0);

    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sample_token(g_ctx, nullptr);
        llama_eval(g_ctx, &token, 1, n_tokens + i);

        const char* piece = llama_token_to_piece(g_model, token);
        if (piece) output += piece;
    }

    env->ReleaseStringUTFChars(prompt, input);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_llmassistant_llm_LLMHandler_close(
        JNIEnv*,
        jobject
) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
}