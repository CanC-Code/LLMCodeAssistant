// File: llama_jni.cpp
// Purpose: JNI bridge for llama.cpp (real inference)
// Target: Android NDK r25+, arm64-v8a

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <cstring>

extern "C" {
#include "llama.h"
}

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// -----------------------------------------------------------------------------
// Global state
// -----------------------------------------------------------------------------

static std::mutex g_mutex;

static llama_model   * g_model = nullptr;
static llama_context * g_ctx   = nullptr;
static const llama_vocab * g_vocab = nullptr;

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

static void release_all() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}

// -----------------------------------------------------------------------------
// JNI
// -----------------------------------------------------------------------------

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaJNI_loadModel(
        JNIEnv * env,
        jobject,
        jstring modelPath,
        jint nCtx,
        jint nThreads
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_all();

    const char * path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = nCtx;
    cparams.n_threads       = nThreads;
    cparams.n_threads_batch = nThreads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        release_all();
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    LOGI("Model loaded (ctx=%d threads=%d)", nCtx, nThreads);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaJNI_generateText(
        JNIEnv * env,
        jobject,
        jstring prompt
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_vocab) {
        LOGE("Model not initialized");
        return env->NewStringUTF("");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    const int prompt_len = (int) strlen(c_prompt);

    // Tokenize
    std::vector<llama_token> tokens(prompt_len + 8);
    int n_prompt_tokens = llama_tokenize(
            g_vocab,
            c_prompt,
            prompt_len,
            tokens.data(),
            tokens.size(),
            true,
            false
    );

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_prompt_tokens <= 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("");
    }

    tokens.resize(n_prompt_tokens);

    // Evaluate prompt
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = false;
    }
    batch.logits[tokens.size() - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        LOGE("Prompt decode failed");
        return env->NewStringUTF("");
    }

    llama_batch_free(batch);

    // Generate tokens
    std::string output;
    const int max_tokens = 128;

    for (int i = 0; i < max_tokens; i++) {
        const float * logits = llama_get_logits(g_ctx);
        int vocab_size = llama_vocab_n_tokens(g_vocab);

        // Greedy sampling
        int best_token = 0;
        float best_logit = logits[0];
        for (int t = 1; t < vocab_size; t++) {
            if (logits[t] > best_logit) {
                best_logit = logits[t];
                best_token = t;
            }
        }

        if (llama_vocab_is_eog(g_vocab, best_token)) {
            break;
        }

        char piece[256];
        int len = llama_token_to_piece(
                g_vocab,
                best_token,
                piece,
                sizeof(piece),
                0,
                false
        );

        if (len > 0) {
            output.append(piece, len);
        }

        llama_batch next = llama_batch_init(1, 0, 1);
        next.token[0]     = best_token;
        next.pos[0]       = tokens.size() + i;
        next.n_seq_id[0]  = 1;
        next.seq_id[0][0] = 0;
        next.logits[0]    = true;

        if (llama_decode(g_ctx, next) != 0) {
            llama_batch_free(next);
            break;
        }

        llama_batch_free(next);
    }

    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaJNI_freeModel(
        JNIEnv *,
        jobject
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_all();
    llama_backend_free();
}