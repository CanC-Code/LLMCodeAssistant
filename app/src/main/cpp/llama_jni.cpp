// File: llama_jni.cpp
// Purpose: JNI bridge for llama.cpp (current API)
// Target: Android (NDK r25+, arm64-v8a)
// Notes:
//  - Matches latest llama_tokenize + llama_token_to_piece signatures
//  - Thread-safe
//  - Minimal but correct foundation

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
// Global state (simple, explicit, predictable)
// -----------------------------------------------------------------------------

static std::mutex g_mutex;

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
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
// JNI
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
    g_model = llama_load_model_from_file(path, mparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        release_all();
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

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

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeTokenize(
        JNIEnv * env,
        jobject /* this */,
        jstring text,
        jboolean addBos
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_vocab) {
        return nullptr;
    }

    const char * c_text = env->GetStringUTFChars(text, nullptr);
    const int text_len = (int) strlen(c_text);

    // First pass: get token count
    int32_t n_tokens = llama_tokenize(
            g_vocab,
            c_text,
            text_len,
            nullptr,
            0,
            addBos,
            false
    );

    if (n_tokens <= 0) {
        env->ReleaseStringUTFChars(text, c_text);
        return nullptr;
    }

    std::vector<llama_token> tokens(n_tokens);

    // Second pass: actual tokenize
    llama_tokenize(
            g_vocab,
            c_text,
            text_len,
            tokens.data(),
            n_tokens,
            addBos,
            false
    );

    env->ReleaseStringUTFChars(text, c_text);

    jintArray out = env->NewIntArray(n_tokens);
    env->SetIntArrayRegion(out, 0, n_tokens, reinterpret_cast<const jint *>(tokens.data()));

    return out;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeTokenToString(
        JNIEnv * env,
        jobject /* this */,
        jint token
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_vocab) {
        return env->NewStringUTF("");
    }

    char buf[256];

    int32_t len = llama_token_to_piece(
            g_vocab,
            (llama_token) token,
            buf,
            sizeof(buf),
            0,
            false
    );

    if (len <= 0) {
        return env->NewStringUTF("");
    }

    return env->NewStringUTF(buf);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llmassistant_llm_LLMHandler_nativeIsEos(
        JNIEnv * /* env */,
        jobject /* this */,
        jint token
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_vocab) {
        return JNI_FALSE;
    }

    return llama_token_is_eog(g_vocab, (llama_token) token)
           ? JNI_TRUE
           : JNI_FALSE;
}