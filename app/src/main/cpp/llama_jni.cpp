#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model*   g_model = nullptr;
static llama_context* g_ctx   = nullptr;
static std::mutex     g_mutex;
static bool           g_backend_initialized = false;

extern "C" {

// -----------------------------------------------------
// nativeInitModel
// -----------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeInitModel(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jint threads
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model || g_ctx) {
        LOGE("Model already initialized");
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    if (!g_backend_initialized) {
        llama_backend_init(false);
        g_backend_initialized = true;
    }

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_load_model_from_file(path, mparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx            = 4096;
    cparams.n_threads        = threads;
    cparams.n_threads_batch  = threads;

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
        jobject /* this */,
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

    // ---- Tokenize ----
    std::vector<llama_token> tokens(
        prompt_str.size() + 16
    );

    int n_tokens = llama_tokenize(
        g_model,
        prompt_str.c_str(),
        prompt_str.size(),
        tokens.data(),
        tokens.size(),
        true,
        true
    );

    if (n_tokens <= 0) {
        return env->NewStringUTF("Tokenization failed");
    }

    tokens.resize(n_tokens);

    // ---- Prefill ----
    llama_batch batch = llama_batch_init(
        512,   // max tokens
        0,     // embd
        1      // seqs
    );

    for (int i = 0; i < n_tokens; ++i) {
        llama_batch_add(
            batch,
            tokens[i],
            i,
            {0},
            i == n_tokens - 1
        );
    }

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("Decode failed");
    }

    // ---- Sampling ----
    llama_sampling_params sparams = llama_sampling_default_params();
    sparams.top_k = 40;
    sparams.top_p = 0.95f;
    sparams.temp  = 0.8f;

    llama_sampling_context* sampler =
        llama_sampling_init(sparams);

    std::string output;
    output.reserve(4096);

    for (int i = 0; i < maxTokens; ++i) {
        llama_token token = llama_sampling_sample(
            sampler,
            g_ctx,
            nullptr
        );

        if (token == llama_token_eos(g_model)) {
            break;
        }

        llama_sampling_accept(
            sampler,
            g_ctx,
            token,
            true
        );

        char piece[32];
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
        llama_batch_add(
            batch,
            token,
            n_tokens + i,
            {0},
            true
        );

        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }
    }

    llama_sampling_free(sampler);
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

    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }

    LOGI("LLM shutdown complete");
}

} // extern "C"