#include <jni.h>
#include <string>
#include <mutex>
#include <vector>
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
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv * env, jobject, jstring modelPath, jint nCtx) {
    std::lock_guard<std::mutex> lock(g_mutex);

    const char * path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);

    if (!g_model) {
        LOGE("Failed to load model: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

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

    [cite_start]// Modern Sampler Chain Configuration[span_0](end_span)
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
    if (!g_ctx || !g_model) return;

    [span_1](start_span)// Reset state for new generation[span_1](end_span)
    llama_kv_cache_clear(g_ctx);

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);

    [span_2](start_span)// Modern Tokenization[span_2](end_span)
    std::vector<llama_token> tokens(strlen(c_prompt) + 4); 
    int n_tokens = llama_tokenize(vocab, c_prompt, strlen(c_prompt), tokens.data(), tokens.size(), true, false);
    tokens.resize(n_tokens);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    // Initial Prompt Decode
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;
    }
    batch.logits[tokens.size() - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return;
    }
    llama_batch_free(batch);

    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");

    [span_3](start_span)// Token Generation Loop [cite: 21-24]
    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[128];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) {
            env->CallVoidMethod(callback, onToken, env->NewStringUTF(std::string(buf, len).c_str()));
        }

        // Single-token batching for auto-regression
        llama_batch next = llama_batch_get_one(&tok, 1);
        next.pos[0] = llama_get_kv_cache_used_cells(g_ctx);

        if (llama_decode(g_ctx, next) != 0) break;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_sampler) llama_sampler_free(g_sampler);
    if (g_ctx) llama_free(g_ctx);
    if (g_model) llama_model_free(g_model);
    g_sampler = nullptr; g_ctx = nullptr; g_model = nullptr;
}
