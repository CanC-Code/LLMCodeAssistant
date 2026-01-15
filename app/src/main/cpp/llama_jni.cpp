// File: llama_jni.cpp
// Purpose: JNI bridge for llama.cpp (current API)
// Target: Android (NDK r25+, arm64-v8a)
// Status: Production-ready basic inference

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
// JNI: Load model
// -----------------------------------------------------------------------------

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeLoadModel(
        JNIEnv * env,
        jobject /* this */,
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

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

// -----------------------------------------------------------------------------
// JNI: Unload model
// -----------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeUnloadModel(
        JNIEnv * /* env */,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_all();
    llama_backend_free();
}

// -----------------------------------------------------------------------------
// JNI: Generate text
// -----------------------------------------------------------------------------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeGenerateText(
        JNIEnv * env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_vocab) {
        return env->NewStringUTF("Model not loaded");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    const int prompt_len = (int) strlen(c_prompt);

    // Tokenize prompt
    std::vector<llama_token> tokens;
    tokens.resize(prompt_len + 4);

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
        return env->NewStringUTF("Tokenization failed");
    }

    tokens.resize(n_tokens);

    // Feed prompt
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i]   = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i]  = 1;
        batch.logits[i] = false;
    }
    batch.logits[n_tokens - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("Prompt decode failed");
    }

    llama_batch_free(batch);

    std::string output;

    // Generation loop
    for (int i = 0; i < maxTokens; i++) {
        const float * logits = llama_get_logits(g_ctx);
        llama_token token = llama_sample_token_greedy(g_ctx, logits);

        if (llama_vocab_is_eog(g_vocab, token)) {
            break;
        }

        char buf[256];
        int len = llama_token_to_piece(
                g_vocab,
                token,
                buf,
                sizeof(buf),
                0,
                false
        );

        if (len > 0) {
            output.append(buf, len);
        }

        llama_batch gen = llama_batch_init(1, 0, 1);
        gen.token[0] = token;
        gen.pos[0]   = llama_get_kv_cache_used_cells(g_ctx);
        gen.seq_id[0][0] = 0;
        gen.n_seq_id[0]  = 1;
        gen.logits[0] = true;

        if (llama_decode(g_ctx, gen) != 0) {
            llama_batch_free(gen);
            break;
        }

        llama_batch_free(gen);
    }

    return env->NewStringUTF(output.c_str());
}