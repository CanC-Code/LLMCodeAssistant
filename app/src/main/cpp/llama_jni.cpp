#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
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
    
    // Initialize backend - mandatory for modern llama.cpp
    llama_backend_init();
    
    // Set up model parameters
    auto mparams = llama_model_default_params();
    
    // Modern API: llama_model_load_from_file
    model = llama_model_load_from_file(path, mparams);
    
    if (!model) {
        LOGE("Failed to load model from %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        llama_backend_free();
        return JNI_FALSE;
    }

    // Set up context parameters
    auto cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    
    // Modern API: llama_init_from_model (replaces llama_new_context_with_model)
    ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        llama_backend_free();
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
        // Clear the entire KV cache
        // seq_id = -1 clears all sequences
        // pos0 = 0, pos1 = -1 clears from position 0 to end
        llama_kv_cache_seq_rm(ctx, -1, 0, -1);
        LOGI("KV cache cleared");
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_llmcodeassistant_LlamaNative_completion(JNIEnv *env, jobject /* thiz */, jstring prompt) {
    if (!ctx || !model) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    // Tokenize the prompt
    std::vector<llama_token> tokens;
    const int n_tokens_prompt = -llama_tokenize(model, prompt_str, strlen(prompt_str), nullptr, 0, true, true);
    tokens.resize(n_tokens_prompt);
    
    if (llama_tokenize(model, prompt_str, strlen(prompt_str), tokens.data(), tokens.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Tokenization failed");
    }
    
    // Clear KV cache before processing new prompt
    llama_kv_cache_seq_rm(ctx, -1, 0, -1);
    
    // Create a batch for the prompt tokens
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    
    // Add tokens to the batch
    for (size_t i = 0; i < tokens.size(); i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    
    // Set the last token to output logits
    batch.logits[batch.n_tokens - 1] = true;
    
    // Process the batch
    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to decode");
    }
    
    // Get logits and sample the next token
    llama_token new_token = llama_sampler_sample(llama_sampler_chain_default_params(), ctx, batch.n_tokens - 1);
    
    // Detokenize the result
    std::string result_str = prompt_str;
    char buf[128];
    int n_chars = llama_token_to_piece(model, new_token, buf, sizeof(buf), 0, true);
    if (n_chars > 0) {
        result_str += std::string(buf, n_chars);
    }
    
    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    return env->NewStringUTF(result_str.c_str());
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
    // Clean up backend resources
    llama_backend_free();
    LOGI("Model unloaded");
}
