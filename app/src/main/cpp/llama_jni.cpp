#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model * model = nullptr;
static llama_context * ctx = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_llmcodeassistant_LlamaNative_loadModel(JNIEnv *env, jobject /* thiz */, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    
    // Initialize backend
    llama_backend_init();
    
    auto mparams = llama_model_default_params();
    // Modern API uses llama_model_load_from_file
    model = llama_model_load_from_file(path, mparams);
    
    if (!model) {
        LOGE("Failed to load model from %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    auto cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    
    // Modern API uses llama_init_from_model
    ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_llmcodeassistant_LlamaNative_clearCache(JNIEnv * /* env */, jobject /* thiz */) {
    if (ctx) {
        // FIX: Replaced 'llama_kv_cache_clear' which was causing the build error.
        // In the latest API, passing -1 to seq_rm clears all sequences and positions.
        llama_kv_cache_seq_rm(ctx, -1, -1, -1);
        LOGI("KV cache cleared");
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_llmcodeassistant_LlamaNative_completion(JNIEnv *env, jobject /* thiz */, jstring prompt) {
    if (!ctx) return env->NewStringUTF("Error: Model not loaded");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    // Inference logic remains as per your implementation requirements.
    std::string result = "Processed: ";
    result += prompt_str;

    env->ReleaseStringUTFChars(prompt, prompt_str);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_llmcodeassistant_LlamaNative_unloadModel(JNIEnv * /* env */, jobject /* thiz */) {
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }
    llama_backend_free();
    LOGI("Model unloaded");
}
