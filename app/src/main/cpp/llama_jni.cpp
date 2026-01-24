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
static llama_model    * g_model   = nullptr;
static llama_context  * g_ctx     = nullptr;
static llama_sampler  * g_sampler = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(
        JNIEnv * env,
        jobject /* thiz */,
        jstring modelPath,
        jint nCtx) {

    std::lock_guard<std::mutex> lock(g_mutex);

    const char * path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = nCtx;
    cparams.n_batch   = 512;
    cparams.n_ubatch  = 512;
    cparams.n_seq_max = 1;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // Sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;

    g_sampler = llama_sampler_chain_init(sparams);

    // Common samplers (adjust as needed)
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(50));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.8f));

    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("Model initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(
        JNIEnv * env,
        jobject /* thiz */,
        jstring prompt,
        jint maxTokens,
        jobject callback) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model || !g_sampler) {
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Model not initialized"));
        return;
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    size_t len = strlen(c_prompt);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    std::vector<llama_token> prompt_tokens(len + 32);
    int32_t n_prompt = llama_tokenize(vocab,
                                       c_prompt, static_cast<int32_t>(len),
                                       prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()),
                                       true, true);

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_prompt < 0) {
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Tokenization failed"));
        return;
    }
    prompt_tokens.resize(n_prompt);

    // Reset KV cache (modern replacement)
    llama_kv_cache_clear(g_ctx);

    // Prepare prompt batch manually
    llama_batch batch = llama_batch_init(static_cast<int32_t>(prompt_tokens.size()), 0, 1);
    batch.n_tokens = static_cast<int32_t>(n_prompt);

    for (int32_t i = 0; i < n_prompt; ++i) {
        batch.token   [i] = prompt_tokens[i];
        batch.pos     [i] = i;
        batch.seq_id  [i][0] = 0;
        batch.n_seq_id[i]    = 1;
        batch.logits  [i]    = (i == n_prompt - 1) ? 1 : 0;
    }

    if (llama_decode(g_ctx, batch)) {
        llama_batch_free(batch);
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Prompt decode failed"));
        return;
    }
    llama_batch_free(batch);

    // Generation loop
    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken    = env->GetMethodID(cls, "onToken",    "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onError    = env->GetMethodID(cls, "onError",    "(Ljava/lang/String;)V");

    std::string output;
    int32_t n_past = n_prompt;
    llama_token eos = llama_vocab_eos(vocab);

    for (int i = 0; i < maxTokens; ++i) {
        llama_token id = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (id == eos) break;

        // Single token batch (manual setup after get_one)
        llama_batch single = llama_batch_get_one(&id, 1);
        single.pos[0]      = n_past;
        single.seq_id[0][0] = 0;
        single.n_seq_id[0] = 1;
        single.logits[0]   = 1;

        if (llama_decode(g_ctx, single)) {
            llama_batch_free(single);
            env->CallVoidMethod(callback, onError, env->NewStringUTF("Decode failed"));
            return;
        }
        llama_batch_free(single);

        char buf[64];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            output += piece;
            env->CallVoidMethod(callback, onToken, env->NewStringUTF(piece.c_str()));
        }

        llama_sampler_accept(g_sampler, id);
        ++n_past;
    }

    env->CallVoidMethod(callback, onComplete, env->NewStringUTF(output.c_str()));
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