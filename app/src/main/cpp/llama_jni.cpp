// File: app/src/main/cpp/llama_jni.cpp
// Purpose: JNI bridge for llama.cpp (current upstream)
// Target: Android NDK r25+, arm64-v8a / armeabi-v7a / x86_64

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

extern "C" {
#include "llama.h"
}

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// -----------------------------------------------------------------------------
// Global state (explicit, simple, predictable)
// -----------------------------------------------------------------------------

static std::mutex g_mutex;

static llama_model *   g_model = nullptr;
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
        llama_free_model(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}

// -----------------------------------------------------------------------------
// JNI: io.canccode.aca.LlamaJNI
// -----------------------------------------------------------------------------

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaJNI_initModel(
        JNIEnv * env,
        jclass /* clazz */,
        jstring modelPath
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    release_all();

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
    cparams.n_ctx = 4096;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        release_all();
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    LOGI("Model initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaJNI_runPrompt(
        JNIEnv * env,
        jclass /* clazz */,
        jstring prompt
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_vocab) {
        return env->NewStringUTF("Model not initialized");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    const int prompt_len = (int) strlen(c_prompt);

    // Tokenize
    std::vector<llama_token> tokens;
    tokens.resize(prompt_len + 8);

    int n_tokens = llama_tokenize(
            g_vocab,
            c_prompt,
            prompt_len,
            tokens.data(),
            tokens.size(),
            true,
            false
    );

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_tokens <= 0) {
        return env->NewStringUTF("");
    }

    tokens.resize(n_tokens);

    // Reset KV cache
    llama_kv_cache_clear(g_ctx);

    // Evaluate prompt
    if (llama_decode(g_ctx, llama_batch_get_one(tokens.data(), tokens.size(), 0, 0)) != 0) {
        return env->NewStringUTF("");
    }

    std::string output;

    const int max_tokens = 512;

    for (int i = 0; i < max_tokens; ++i) {
        const float * logits = llama_get_logits(g_ctx);
        const int vocab_size = llama_vocab_n_tokens(g_vocab);

        // Greedy sampling (correct, deterministic baseline)
        int best_token = 0;
        float best_logit = logits[0];

        for (int t = 1; t < vocab_size; ++t) {
            if (logits[t] > best_logit) {
                best_logit = logits[t];
                best_token = t;
            }
        }

        if (llama_token_is_eog(g_vocab, best_token)) {
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

        llama_token tok = (llama_token) best_token;
        if (llama_decode(g_ctx, llama_batch_get_one(&tok, 1, tokens.size() + i, 0)) != 0) {
            break;
        }
    }

    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaJNI_release(
        JNIEnv * /* env */,
        jclass /* clazz */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_all();
    llama_backend_free();
}