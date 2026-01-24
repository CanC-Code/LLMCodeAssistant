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

    // Use new API: llama_model_load_from_file
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // Use new API: llama_init_from_model
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // Initialize sampler
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());

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

    // Use new tokenization API
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    
    // First, count tokens needed
    int n_tokens = llama_tokenize(
        vocab,
        c_prompt,
        strlen(c_prompt),
        nullptr,
        0,
        true,  // add_special
        true   // parse_special
    );

    if (n_tokens < 0) {
        n_tokens = -n_tokens;
    }

    std::vector<llama_token> tokens(n_tokens);
    
    // Actually tokenize
    n_tokens = llama_tokenize(
        vocab,
        c_prompt,
        strlen(c_prompt),
        tokens.data(),
        tokens.size(),
        true,
        true
    );

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_tokens <= 0) {
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError,
                            env->NewStringUTF("Tokenization failed"));
        return;
    }

    tokens.resize(n_tokens);

    // Clear KV cache - proper API
    llama_kv_cache_seq_rm(g_ctx, 0, -1, -1);

    // Create batch for prompt - manual population
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    
    for (size_t i = 0; i < tokens.size(); i++) {
        batch.token[batch.n_tokens] = tokens[i];
        batch.pos[batch.n_tokens] = i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = false;
        batch.n_tokens++;
    }
    
    // Mark last token for logits
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

    // Get EOS token using new API
    llama_token eos = llama_vocab_eos(vocab);

    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");

    std::string output;
    int n_cur = tokens.size();

    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        
        if (tok == eos) break;

        // Convert token to text using new API
        char buf[128];
        int len = llama_token_to_piece(
            vocab,
            tok,
            buf,
            sizeof(buf),
            0,
            true
        );
        
        if (len > 0) {
            std::string piece(buf, len);
            output += piece;
            env->CallVoidMethod(callback, onToken,
                                env->NewStringUTF(piece.c_str()));
        }

        // Create batch for next token - manual population
        llama_batch next = llama_batch_init(1, 0, 1);
        next.token[0] = tok;
        next.pos[0] = n_cur;
        next.n_seq_id[0] = 1;
        next.seq_id[0][0] = 0;
        next.logits[0] = true;
        next.n_tokens = 1;

        if (llama_decode(g_ctx, next) != 0) {
            llama_batch_free(next);
            env->CallVoidMethod(callback, onError,
                                env->NewStringUTF("Generation decode failed"));
            return;
        }
        
        llama_batch_free(next);
        n_cur++;
    }

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
}