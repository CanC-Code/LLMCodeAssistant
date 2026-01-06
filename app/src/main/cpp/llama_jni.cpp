#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::mutex g_mutex;

extern "C" {

// -----------------------------------------------------
// nativeInitModel
// -----------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeInitModel(
        JNIEnv* env,
        jobject,
        jstring modelPath,
        jint threads
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model || g_ctx) {
        LOGE("Model already initialized");
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init(false);

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_load_model_from_file(path, mparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 4096;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("LLM initialized successfully");
    return JNI_TRUE;
}

// -----------------------------------------------------
// nativeInfer
// -----------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeInfer(
        JNIEnv* env,
        jobject,
        jstring prompt,
        jint maxTokens
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model) {
        return env->NewStringUTF("LLM not initialized");
    }

    const char* input = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(input);
    env->ReleaseStringUTFChars(prompt, input);

    std::vector<llama_token> tokens(prompt_str.size() + 8);
    int n_tokens = llama_tokenize(
            g_model,
            prompt_str.c_str(),
            prompt_str.length(),
            tokens.data(),
            tokens.size(),
            true,
            true
    );

    if (n_tokens <= 0) {
        return env->NewStringUTF("Tokenization failed");
    }

    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(512, 0, 1);

    for (int i = 0; i < n_tokens; ++i) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("Decode failed");
    }

    std::string output;
    output.reserve(4096);

    for (int i = 0; i < maxTokens; ++i) {
        const float* logits = llama_get_logits_ith(g_ctx, batch.n_tokens - 1);

        llama_token token = llama_sample_token(
                g_ctx,
                llama_sample_top_p_top_k(
                        g_ctx,
                        logits,
                        40,
                        0.95f,
                        1.0f,
                        1.0f
                )
        );

        if (token == llama_token_eos(g_model)) {
            break;
        }

        char piece[8];
        int len = llama_token_to_piece(
                g_model,
                token,
                piece,
                sizeof(piece),
                0,
                true
        );

        if (len > 0) {
            output.append(piece, len);
        }

        llama_batch_clear(batch);
        llama_batch_add(batch, token, batch.n_tokens, {0}, true);

        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }
    }

    llama_batch_free(batch);
    return env->NewStringUTF(output.c_str());
}

// -----------------------------------------------------
// nativeClose
// -----------------------------------------------------
JNIEXPORT void JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeClose(
        JNIEnv*,
        jobject
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    llama_backend_free();
    LOGI("LLM shutdown complete");
}

} // extern "C"