// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI bridge for llama.cpp (Android, local GGUF, sampler-chain based)

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

static llama_model   * g_model   = nullptr;
static llama_context * g_ctx     = nullptr;
static llama_sampler * g_sampler = nullptr;
static bool g_backend_initialized = false;

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
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Model load failed");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = 0;
    cparams.n_threads_batch = 0;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Context creation failed");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);

    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.72f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.92f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_typical(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Model + context + sampler ready");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(
        JNIEnv * env,
        jobject,
        jstring prompt,
        jint maxTokens) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model || !g_sampler) {
        return env->NewStringUTF("[Model not initialized]");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    size_t len = strlen(c_prompt);

    LOGI("Prompt (%zu chars): %s", len, c_prompt);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    std::vector<llama_token> tokens(len + 64);
    int n = llama_tokenize(vocab, c_prompt, len, tokens.data(), tokens.size(), true, false);

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n <= 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("[Tokenization failed]");
    }

    tokens.resize(n);

    llama_batch batch = llama_batch_init(n, 0, 1);
    for (int i = 0; i < n; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n - 1);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("[Failed to process prompt]");
    }
    llama_batch_free(batch);

    std::string output;
    int pos = n;

    for (int i = 0; i < maxTokens; ++i) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (tok == llama_vocab_eos(vocab) || tok == llama_vocab_eot(vocab)) {
            break;
        }

        char buf[64];
        int l = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (l > 0) output.append(buf, l);

        llama_batch b = llama_batch_init(1, 0, 1);
        b.token[0] = tok;
        b.pos[0] = pos++;
        b.n_seq_id[0] = 1;
        b.seq_id[0][0] = 0;
        b.logits[0] = true;

        if (llama_decode(g_ctx, b) != 0) {
            llama_batch_free(b);
            break;
        }
        llama_batch_free(b);
    }

    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(
        JNIEnv *,
        jobject) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }

    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }

    LOGI("Shutdown complete");
}