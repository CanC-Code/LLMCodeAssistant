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

// Load model from file
JNIEXPORT jboolean JNICALL
Java_com_yourpackage_LlamaJNI_loadModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model || g_ctx) {
        LOGE("Model already loaded");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    llama_model_params params = llama_model_default_params();
    params.n_threads = 4; // adjust thread count for Android
    g_model = llama_load_model_from_file(path, params);

    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    g_ctx = llama_new_context(g_model, nullptr);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

// Unload model
JNIEXPORT void JNICALL
Java_com_yourpackage_LlamaJNI_unloadModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_free_context(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
}

// Tokenize a string
JNIEXPORT jintArray JNICALL
Java_com_yourpackage_LlamaJNI_tokenize(JNIEnv* env, jobject thiz, jstring text) {
    const char* str = env->GetStringUTFChars(text, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx) {
        env->ReleaseStringUTFChars(text, str);
        return nullptr;
    }

    std::vector<llama_token> tokens(1024);
    int n = llama_tokenize(g_ctx, str, tokens.data(), tokens.size(), true);
    if (n < 0) n = 0;

    jintArray result = env->NewIntArray(n);
    env->SetIntArrayRegion(result, 0, n, tokens.data());
    env->ReleaseStringUTFChars(text, str);
    return result;
}

// Sample a token with updated sampler API
JNIEXPORT jint JNICALL
Java_com_yourpackage_LlamaJNI_sampleToken(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx) return -1;

    struct llama_sampler_data sparams;
    llama_sampler_data_default(&sparams);
    sparams.top_k = 40;
    sparams.top_p = 0.95f;
    sparams.temp = 0.8f;

    struct llama_sampler* sampler = llama_sampler_init(g_ctx, &sparams);
    if (!sampler) return -1;

    llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
    llama_sampler_accept(sampler, token);
    llama_sampler_free(sampler);

    return (jint)token;
}

// Convert token to string piece
JNIEXPORT jstring JNICALL
Java_com_yourpackage_LlamaJNI_tokenToPiece(JNIEnv* env, jobject thiz, jint token) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx) return nullptr;

    char buf[256];
    llama_token_to_piece(g_ctx, (llama_token)token, buf, sizeof(buf), 0, true);
    return env->NewStringUTF(buf);
}

} // extern "C"