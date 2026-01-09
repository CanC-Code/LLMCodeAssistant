#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <cstring>
#include <cstdlib>

#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global llama objects
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::mutex g_mutex;

// Utility function: load model
extern "C" JNIEXPORT jboolean JNICALL
Java_com_yourpackage_LlamaJNI_loadModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    // Initialize backend
    llama_backend_init();

    // Model parameters
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model from path: %s", path);
        return JNI_FALSE;
    }

    // Context parameters
    llama_context_params cparams = llama_context_default_params();
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to initialize context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

// Utility function: free model
extern "C" JNIEXPORT void JNICALL
Java_com_yourpackage_LlamaJNI_freeModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

// Tokenize input string
extern "C" JNIEXPORT jintArray JNICALL
Java_com_yourpackage_LlamaJNI_tokenize(JNIEnv* env, jobject thiz, jstring text) {
    const char* str = env->GetStringUTFChars(text, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx) {
        env->ReleaseStringUTFChars(text, str);
        return nullptr;
    }

    std::vector<llama_token> tokens(1024);
    int n_tokens = llama_tokenize(&g_model->vocab, str, tokens.data(), tokens.size(), true);
    env->ReleaseStringUTFChars(text, str);

    jintArray result = env->NewIntArray(n_tokens);
    env->SetIntArrayRegion(result, 0, n_tokens, reinterpret_cast<jint*>(tokens.data()));
    return result;
}

// Generate text from tokens
extern "C" JNIEXPORT jstring JNICALL
Java_com_yourpackage_LlamaJNI_generate(JNIEnv* env, jobject thiz, jintArray inputTokens, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx) return env->NewStringUTF("");

    // Copy input tokens
    jsize len = env->GetArrayLength(inputTokens);
    std::vector<llama_token> tokens(len);
    env->GetIntArrayRegion(inputTokens, 0, len, reinterpret_cast<jint*>(tokens.data()));

    // Prepare sampler
    llama_sampler_params sparams = llama_sampler_default_params();
    sparams.top_k = 40;
    sparams.top_p = 0.95f;
    sparams.temp = 0.8f;

    llama_sampler_context_t* sampler = llama_sampler_init(&g_ctx->vocab, sparams);

    std::string output;

    for (int i = 0; i < maxTokens; ++i) {
        llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        if (token == llama_vocab_eos(&g_model->vocab)) break;

        llama_sampler_accept(sampler, g_ctx, token, true);

        char piece[256];
        int piece_len = llama_token_to_piece(&g_model->vocab, token, piece, sizeof(piece), 0, true);
        output.append(piece, piece_len);
    }

    llama_sampler_free(sampler);

    return env->NewStringUTF(output.c_str());
}