#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_context * g_ctx = nullptr;
static llama_model * g_model = nullptr;
static llama_sampler * g_sampler = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(
        JNIEnv * env,
        jobject,
        jstring modelPath,
        jint nCtx) {

    std::lock_guard<std::mutex> lock(g_mutex);

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing model from: %s", path);

    // Use new API
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = 512;
    cparams.n_threads = 4;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // Create sampler chain with modern API
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(1234));

    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("Model initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(
        JNIEnv * env,
        jobject,
        jstring prompt,
        jint maxTokens,
        jobject callback) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model || !g_sampler) {
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError,
                            env->NewStringUTF("Model not initialized"));
        return;
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generating response for: %s", c_prompt);

    // Tokenize with new API
    const int n_prompt_tokens = -llama_tokenize(
        llama_model_get_vocab(g_model),
        c_prompt,
        strlen(c_prompt),
        nullptr,
        0,
        true,
        false
    );

    std::vector<llama_token> tokens(n_prompt_tokens);
    
    llama_tokenize(
        llama_model_get_vocab(g_model),
        c_prompt,
        strlen(c_prompt),
        tokens.data(),
        tokens.size(),
        true,
        false
    );

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (tokens.empty()) {
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError,
                            env->NewStringUTF("Tokenization failed"));
        return;
    }

    // Clear KV cache
    llama_kv_cache_clear(g_ctx);

    // Process prompt
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError,
                            env->NewStringUTF("Prompt decode failed"));
        return;
    }

    llama_batch_free(batch);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    llama_token eos = llama_vocab_eos(vocab);

    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");

    std::string output;
    int n_cur = tokens.size();

    // Generation loop
    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        
        if (llama_vocab_is_eog(vocab, tok)) {
            LOGI("EOS token reached");
            break;
        }

        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        
        if (len > 0) {
            std::string piece(buf, len);
            output += piece;
            env->CallVoidMethod(callback, onToken,
                                env->NewStringUTF(piece.c_str()));
        }

        // Prepare next batch
        llama_batch next = llama_batch_init(1, 0, 1);
        llama_batch_add(next, tok, n_cur, {0}, true);

        if (llama_decode(g_ctx, next) != 0) {
            llama_batch_free(next);
            env->CallVoidMethod(callback, onError,
                                env->NewStringUTF("Generation decode failed"));
            return;
        }
        
        llama_batch_free(next);
        n_cur++;
    }

    LOGI("Generation complete: %d tokens", (int)output.length());
    env->CallVoidMethod(callback, onComplete,
                        env->NewStringUTF(output.c_str()));
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(
        JNIEnv *,
        jobject) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    LOGI("Native resources freed");
}