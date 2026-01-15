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

static llama_model  * g_model  = nullptr;
static llama_context* g_ctx    = nullptr;
static llama_sampler* g_sampler = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_init(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jint nCtx
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Create greedy sampler
    g_sampler = llama_sampler_init(llama_sampler_greedy());
    if (!g_sampler) {
        LOGE("Failed to create sampler");
        llama_free(g_ctx);
        llama_model_free(g_model);
        g_ctx = nullptr;
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("llama initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_generate(
        JNIEnv* env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model || !g_ctx || !g_sampler) {
        return env->NewStringUTF("[llama not initialized]");
    }

    const char* prompt_c = env->GetStringUTFChars(prompt, nullptr);

    const llama_vocab* vocab = llama_model_get_vocab(g_model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(prompt_c) + 8);

    int n_tokens = llama_tokenize(
        vocab,
        prompt_c,
        strlen(prompt_c),
        tokens.data(),
        tokens.size(),
        true,
        true
    );

    env->ReleaseStringUTFChars(prompt, prompt_c);

    if (n_tokens < 0) {
        return env->NewStringUTF("[tokenization failed]");
    }

    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i]   = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i] = 1;
        batch.logits[i] = false;
    }
    batch.logits[tokens.size() - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("[decode failed]");
    }

    std::string output;

    for (int i = 0; i < maxTokens; ++i) {
        llama_sampler_sample(g_sampler, g_ctx, -1);

        llama_token token = llama_sampler_get_last(g_sampler);

        if (token == llama_vocab_eos(vocab)) {
            break;
        }

        char piece[128];
        int len = llama_token_to_piece(
            vocab,
            token,
            piece,
            sizeof(piece),
            0,
            true
        );

        if (len > 0) {
            output.append(piece, len);
        }

        llama_batch batch2 = llama_batch_init(1, tokens.size() + i, 1);
        batch2.token[0] = token;
        batch2.pos[0] = tokens.size() + i;
        batch2.seq_id[0][0] = 0;
        batch2.n_seq_id[0] = 1;
        batch2.logits[0] = true;

        if (llama_decode(g_ctx, batch2) != 0) {
            llama_batch_free(batch2);
            break;
        }

        llama_batch_free(batch2);
    }

    llama_batch_free(batch);

    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdown(
        JNIEnv* /* env */,
        jobject /* this */
) {
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
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_free();
}