// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI bridge for llama.cpp – local GGUF loading and text generation
// Copyright: CanC-code - CCVO

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;

static llama_model   * g_model   = nullptr;
static llama_context * g_ctx     = nullptr;
static llama_sampler * g_sampler = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(
        JNIEnv * env,
        jobject,
        jstring modelPath,
        jint nCtx) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_free_model(g_model);     g_model   = nullptr; }

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_load_model_from_file(path, mparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Model load failed");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Context creation failed");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    llama_sampler_params sparams = llama_sampler_default_params();
    sparams.temp = 0.7f;
    sparams.top_k = 40;
    sparams.top_p = 0.95f;
    sparams.seed = LLAMA_DEFAULT_SEED;

    g_sampler = llama_sampler_init(sparams);

    LOGI("Model initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(
        JNIEnv * env,
        jobject,
        jstring prompt,
        jint maxTokens,
        jobject callback) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model || !g_sampler) {
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError,
                            env->NewStringUTF("Model not initialized"));
        return;
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);

    std::vector<llama_token> tokens;
    tokens.resize(c_prompt ? strlen(c_prompt) + 8 : 8);

    int n_tokens = llama_tokenize(
        llama_model_get_vocab(g_model),
        c_prompt,
        strlen(c_prompt),
        tokens.data(),
        tokens.size(),
        true,
        true
    );

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_tokens <= 0) {
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError,
                            env->NewStringUTF("Tokenization failed"));
        return;
    }

    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i] = 1;
    }
    batch.logits[n_tokens - 1] = 1;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError,
                            env->NewStringUTF("Prompt decode failed"));
        return;
    }

    llama_batch_free(batch);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    llama_token eos = llama_vocab_eos(vocab);

    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken    = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onError    = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");

    std::string output;

    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        if (tok == eos) break;

        char buf[128];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);

        if (len > 0) {
            std::string piece(buf, len);
            output += piece;
            env->CallVoidMethod(callback, onToken,
                                env->NewStringUTF(piece.c_str()));
        }

        llama_batch next = llama_batch_init(1, 0, 1);
        next.token[0] = tok;
        next.pos[0] = llama_get_kv_cache_used_cells(g_ctx);
        next.seq_id[0][0] = 0;
        next.n_seq_id[0] = 1;
        next.logits[0] = 1;

        if (llama_decode(g_ctx, next) != 0) {
            llama_batch_free(next);
            env->CallVoidMethod(callback, onError,
                                env->NewStringUTF("Generation decode failed"));
            return;
        }
        llama_batch_free(next);
    }

    env->CallVoidMethod(callback, onComplete,
                        env->NewStringUTF(output.c_str()));
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(
        JNIEnv *,
        jobject) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_free_model(g_model);     g_model   = nullptr; }

    LOGI("Llama shutdown complete");
}