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
static llama_sampler * sampler = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_llmcodeassistant_LlamaNative_loadModel(JNIEnv *env, jobject /* thiz */, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    
    // Initialize backend
    llama_backend_init();
    
    // Set up model parameters
    llama_model_params mparams = llama_model_default_params();
    
    // Load model
    model = llama_model_load_from_file(path, mparams);
    
    if (!model) {
        LOGE("Failed to load model from %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        llama_backend_free();
        return JNI_FALSE;
    }

    // Set up context parameters
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    cparams.n_threads = 4;
    
    // Create context
    ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        llama_backend_free();
        return JNI_FALSE;
    }

    // Create sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sparams);
    
    // Add greedy sampler
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    
    LOGI("Model loaded successfully");
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_llmcodeassistant_LlamaNative_clearCache(JNIEnv * /* env */, jobject /* thiz */) {
    if (ctx) {
        // Recreate context to clear cache
        // This is the safest approach when specific cache functions aren't available
        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = 2048;
        cparams.n_batch = 512;
        cparams.n_threads = 4;
        
        llama_free(ctx);
        ctx = llama_init_from_model(model, cparams);
        
        if (ctx) {
            LOGI("Context recreated (cache cleared)");
        } else {
            LOGE("Failed to recreate context");
        }
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_llmcodeassistant_LlamaNative_completion(JNIEnv *env, jobject /* thiz */, jstring prompt) {
    if (!ctx || !model || !sampler) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    // Get the model's vocabulary
    const llama_vocab * vocab = llama_model_get_vocab(model);
    if (!vocab) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to get vocabulary");
    }
    
    // Tokenize the prompt
    std::vector<llama_token> tokens;
    const int n_tokens_prompt = -llama_tokenize(vocab, prompt_str, strlen(prompt_str), nullptr, 0, true, true);
    
    if (n_tokens_prompt <= 0) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Tokenization failed");
    }
    
    tokens.resize(n_tokens_prompt);
    
    if (llama_tokenize(vocab, prompt_str, strlen(prompt_str), tokens.data(), tokens.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Tokenization failed");
    }
    
    // Create batch
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    
    // Fill batch with tokens
    for (size_t i = 0; i < tokens.size(); i++) {
        batch.token[batch.n_tokens] = tokens[i];
        batch.pos[batch.n_tokens] = i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = (i == tokens.size() - 1);
        batch.n_tokens++;
    }
    
    // Decode the batch
    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Decode failed");
    }
    
    // Sample next token
    llama_token new_token = llama_sampler_sample(sampler, ctx, batch.n_tokens - 1);
    
    // Convert token back to text
    std::string result_str = prompt_str;
    char buf[128];
    int n_chars = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
    if (n_chars > 0) {
        result_str += std::string(buf, n_chars);
    }
    
    // Cleanup
    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    return env->NewStringUTF(result_str.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_llmcodeassistant_LlamaNative_unloadModel(JNIEnv * /* env */, jobject /* thiz */) {
    // Free sampler
    if (sampler) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }
    
    // Free context
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    
    // Free model
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }
    
    // Free backend
    llama_backend_free();
    
    LOGI("Model unloaded");
}
