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

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_sampler * g_sampler = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(
        JNIEnv * env,
        jobject /* this */,
        jstring modelPath,
        jint nCtx) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx || g_model) {
        LOGI("llama already initialized");
        return JNI_TRUE;
    }

    const char * path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_load_model_from_file(path, mparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = 0; // auto
    cparams.n_threads_batch = 0;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // -----------------------------
    // Sampler chain (modern API)
    // -----------------------------
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);

    // Greedy sampling (deterministic, safe default)
    llama_sampler_chain_add(
        g_sampler,
        llama_sampler_init_greedy()
    );

    LOGI("llama initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(
        JNIEnv * env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model || !g_sampler) {
        return env->NewStringUTF("[llama not initialized]");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(c_prompt) + 8);

    int n = llama_tokenize(
        g_model,
        c_prompt,
        (int) strlen(c_prompt),
        tokens.data(),
        tokens.size(),
        true,
        true
    );

    env->ReleaseStringUTFChars(prompt, c_prompt);

    tokens.resize(n);

    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i]   = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;
    }
    batch.logits[tokens.size() - 1] = true;

    llama_decode(g_ctx, batch);
    llama_batch_free(batch);

    std::string result;

    for (int i = 0; i < maxTokens; ++i) {

        llama_token token = llama_sampler_sample(
            g_sampler,
            g_ctx,
            -1
        );

        if (token == llama_token_eos(g_model)) {
            break;
        }

        llama_batch b = llama_batch_init(1, 0, 1);
        b.token[0] = token;
        b.pos[0] = tokens.size();
        b.n_seq_id[0] = 1;
        b.seq_id[0][0] = 0;
        b.logits[0] = true;

        llama_decode(g_ctx, b);
        llama_batch_free(b);

        tokens.push_back(token);

        char buf[8];
        int len = llama_token_to_piece(
            g_model,
            token,
            buf,
            sizeof(buf),
            0,
            true
        );

        if (len > 0) {
            result.append(buf, len);
        }
    }

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(
        JNIEnv *,
        jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    llama_backend_free();
    LOGI("llama shutdown complete");
}