#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LLMHandler_nativeLoadModel(
        JNIEnv * env,
        jobject /* this */,
        jstring modelPath
) {
    const char * path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_load_model_from_file(path, model_params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);

    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LLMHandler_nativePrompt(
        JNIEnv * env,
        jobject /* this */,
        jstring prompt
) {
    if (!g_ctx || !g_model) {
        return env->NewStringUTF("Model not loaded");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(c_prompt) + 8);

    int n_tokens = llama_tokenize(
            g_model,
            c_prompt,
            tokens.data(),
            tokens.size(),
            true,
            false
    );

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n_tokens <= 0) {
        return env->NewStringUTF("Tokenization failed");
    }

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i]   = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i] = 1;
        batch.logits[i] = false;
    }
    batch.logits[n_tokens - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("Decode failed");
    }

    llama_batch_free(batch);

    llama_sampler * sampler = llama_sampler_init_greedy();

    std::string output;

    for (int i = 0; i < 128; i++) {
        llama_token token = llama_sampler_sample(sampler, g_ctx, -1);

        if (token == llama_token_eos(g_model)) {
            break;
        }

        char buf[8];
        int len = llama_token_to_piece(g_ctx, token, buf, sizeof(buf), 0, false);
        if (len > 0) {
            output.append(buf, len);
        }

        llama_batch next = llama_batch_init(1, n_tokens + i, 1);
        next.token[0] = token;
        next.pos[0]   = n_tokens + i;
        next.seq_id[0][0] = 0;
        next.n_seq_id[0] = 1;
        next.logits[0] = true;

        llama_decode(g_ctx, next);
        llama_batch_free(next);
    }

    llama_sampler_free(sampler);

    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LLMHandler_nativeUnloadModel(
        JNIEnv * /* env */,
        jobject /* this */
) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_free();
}