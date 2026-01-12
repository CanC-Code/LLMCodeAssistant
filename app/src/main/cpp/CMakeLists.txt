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

static llama_model* model = nullptr;
static llama_context* ctx = nullptr;
static std::mutex llama_mutex;

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaJNI_loadModel(JNIEnv* env, jobject thiz, jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(llama_mutex);

    if (model) {
        llama_model_free(model);
        model = nullptr;
    }

    // Default model parameters (adjust as needed)
    llama_model_load_params params;
    params.n_ctx = 512;      // context size
    params.n_parts = -1;     // auto
    params.n_threads = 4;    // default threads
    params.low_vram = false;
    params.vocab_only = false;

    model = llama_load_model_from_file(path, params);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGE("Failed to load model from path: %s", path);
        return JNI_FALSE;
    }

    // Initialize context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_threads = 4;
    ctx_params.n_batch = 8;

    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded and context initialized successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaJNI_generateText(JNIEnv* env, jobject thiz, jstring jprompt) {
    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (!prompt) return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(llama_mutex);

    if (!ctx) {
        env->ReleaseStringUTFChars(jprompt, prompt);
        return env->NewStringUTF("Error: Context not initialized");
    }

    // Tokenize prompt
    std::vector<llama_token> tokens;
    tokens.resize(strlen(prompt) * 2); // rough estimate
    int n_tokens = llama_tokenize(ctx, prompt, tokens.data(), tokens.size(), false);
    tokens.resize(n_tokens);

    // Feed prompt to model
    if (llama_eval(ctx, tokens.data(), tokens.size(), 0, 0) != 0) {
        env->ReleaseStringUTFChars(jprompt, prompt);
        return env->NewStringUTF("Error: llama_eval failed");
    }

    // Sample next tokens
    std::string result;
    const int max_tokens = 64;
    for (int i = 0; i < max_tokens; ++i) {
        llama_token token = llama_sample_token(ctx, nullptr);
        if (token == LLAMA_TOKEN_EOS) break;

        const char* token_str = llama_token_to_str(ctx, token);
        if (!token_str) break;

        result += token_str;

        // Evaluate new token
        if (llama_eval(ctx, &token, 1, 0, 0) != 0) break;
    }

    env->ReleaseStringUTFChars(jprompt, prompt);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaJNI_freeModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(llama_mutex);

    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }
    LOGI("Model and context freed");
}