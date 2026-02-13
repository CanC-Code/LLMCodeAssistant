#include <jni.h>
#include <string>
#include <mutex>
#include <vector>
#include <android/log.h>
#include <cstring>

// Include the public llama.cpp API
#include "llama.h"

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global variables to hold the model state
static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_sampler * g_sampler = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv * env, jobject, jstring modelPath, jint nCtx) {
    std::lock_guard<std::mutex> lock(g_mutex);

    const char * path = env->GetStringUTFChars(modelPath, nullptr);

    // 1. Initialize Model
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);

    if (!g_model) {
        LOGE("Failed to load model: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // 2. Initialize Context
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

    // 3. Initialize Sampler
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("Llama initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv * env, jobject, jstring prompt, jint maxTokens, jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model || !g_sampler) return;

    // FIX: Replaced 'llama_kv_cache_clear' with standard sequence removal API
    llama_kv_cache_seq_rm(g_ctx, -1, -1, -1);

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);

    // 1. Tokenize the input prompt
    std::vector<llama_token> tokens(strlen(c_prompt) + 32);
    int n_tokens = llama_tokenize(vocab, c_prompt, (int)strlen(c_prompt), tokens.data(), (int)tokens.size(), true, false);

    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, c_prompt, (int)strlen(c_prompt), tokens.data(), (int)tokens.size(), true, false);
    }
    tokens.resize(n_tokens);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_tokens <= 0) return;

    // 2. Decode (Pre-fill) the prompt
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (int i = 0; i < (int)tokens.size(); i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == (int)tokens.size() - 1);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("llama_decode failed");
        llama_batch_free(batch);
        return;
    }
    llama_batch_free(batch);

    // Setup Java callback method ID
    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");

    // 3. Generation Loop
    int n_past = n_tokens;
    for (int i = 0; i < maxTokens; i++) {
        // Sample the next token
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Check for End-of-Generation (EOG) token
        if (llama_vocab_is_eog(vocab, tok)) {
            break;
        }

        // Convert token to string and send to Java
        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) {
            jstring jstr = env->NewStringUTF(std::string(buf, len).c_str());
            env->CallVoidMethod(callback, onToken, jstr);

            // CRITICAL FIX: Delete local reference to prevent JNI table overflow
            env->DeleteLocalRef(jstr);
        }

        // Prepare the next batch with the single generated token
        llama_batch next = llama_batch_get_one(&tok, 1);
        next.pos[0] = n_past;

        // Decode the next token
        if (llama_decode(g_ctx, next) != 0) {
            break;
        }
        n_past++;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    // Cleanup resources
    if (g_sampler) llama_sampler_free(g_sampler);
    if (g_ctx) llama_free(g_ctx);
    if (g_model) llama_model_free(g_model);
    g_sampler = nullptr; g_ctx = nullptr; g_model = nullptr;
}
