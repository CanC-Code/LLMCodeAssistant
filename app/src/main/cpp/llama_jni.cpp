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
    cparams.n_batch   = 512;   // adjust based on RAM
    cparams.n_ubatch  = 512;
    cparams.n_seq_max = 1;     // single sequence

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // Build sampler chain (modern style)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;

    g_sampler = llama_sampler_chain_init(sparams);

    // Add typical samplers - tune values as needed
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());  // or dist for sampling
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(50));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.8f));
    // Add repetition/penalty if desired:
    // llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(64, 1.1f, 0.85f, 1.0f));

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

    // Tokenize - modern signature
    std::vector<llama_token> prompt_tokens;
    prompt_tokens.resize(len + 32);  // safety margin

    int32_t n_prompt = llama_tokenize(vocab,
                                       c_prompt, static_cast<int32_t>(len),
                                       prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()),
                                       true,   // add_special
                                       true);  // parse_special

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_prompt < 0) {
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Tokenization failed (buffer too small?)"));
        return;
    }
    prompt_tokens.resize(n_prompt);

    // Reset KV cache for seq 0
    llama_kv_cache_seq_rm(g_ctx, 0, -1, -1);  // clear entire sequence

    // Process prompt in batch
    llama_batch batch = llama_batch_init(static_cast<int32_t>(prompt_tokens.size()), 0, 1);
    for (int32_t i = 0; i < n_prompt; ++i) {
        llama_batch_add(batch, prompt_tokens[i], i, {0}, i == n_prompt - 1);  // logits only on last
    }

    if (llama_decode(g_ctx, batch)) {
        llama_batch_free(batch);
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Prompt decode failed"));
        return;
    }
    llama_batch_free(batch);

    // Generation
    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken    = env->GetMethodID(cls, "onToken",    "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onError    = env->GetMethodID(cls, "onError",    "(Ljava/lang/String;)V");

    std::string output;
    int32_t n_past = n_prompt;
    llama_token eos = llama_token_eos(vocab);  // or llama_vocab_eos_token(vocab) in some versions

    for (int i = 0; i < maxTokens; ++i) {
        llama_token id = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (id == eos) break;

        // Decode single token
        llama_batch single = llama_batch_get_one(&id, 1, n_past, 0);
        if (llama_decode(g_ctx, single)) {
            llama_batch_free(single);
            env->CallVoidMethod(callback, onError, env->NewStringUTF("Decode failed"));
            return;
        }
        llama_batch_free(single);

        // To piece
        char buf[64];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            output += piece;
            env->CallVoidMethod(callback, onToken, env->NewStringUTF(piece.c_str()));
        }

        llama_sampler_accept(g_sampler, id);  // crucial for stateful samplers
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