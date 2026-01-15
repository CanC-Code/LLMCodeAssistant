#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static std::mutex g_mutex;

extern "C" {

// ------------------------------------------------------------
// Initialize model + context
// ------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initModel(
        JNIEnv * env,
        jobject /* thiz */,
        jstring modelPath
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    const char * path = env->GetStringUTFChars(modelPath, nullptr);

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    llama_backend_init();

    // ---- Model params ----
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;   // CPU-only for Android

    g_model = llama_load_model_from_file(path, mparams);
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // ---- Context params ----
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_threads = 4;
    cparams.n_batch = 512;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("Model + context initialized");
    return JNI_TRUE;
}

// ------------------------------------------------------------
// Run prompt (minimal reference implementation)
// ------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_runPrompt(
        JNIEnv * env,
        jobject /* thiz */,
        jstring prompt
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model) {
        return env->NewStringUTF("Model not initialized");
    }

    const char * text = env->GetStringUTFChars(prompt, nullptr);

    const int max_tokens = 1024;
    std::vector<llama_token> tokens(max_tokens);

    int n_tokens = llama_tokenize(
            g_model,
            text,
            strlen(text),
            tokens.data(),
            tokens.size(),
            true,   // add BOS
            false   // special
    );

    if (n_tokens < 0) {
        env->ReleaseStringUTFChars(prompt, text);
        return env->NewStringUTF("Tokenization failed");
    }

    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(
            tokens.size(),
            0,
            1
    );

    for (size_t i = 0; i < tokens.size(); ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i]   = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i]  = 1;
        batch.logits[i]    = false;
    }
    batch.logits[tokens.size() - 1] = true;
    batch.n_tokens = tokens.size();

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, text);
        return env->NewStringUTF("Decode failed");
    }

    const llama_token eos = llama_token_eos(g_model);
    std::string output;

    for (int i = 0; i < 128; ++i) {
        const float * logits = llama_get_logits(g_ctx);
        llama_token token = llama_sample_token_greedy(g_ctx, logits);

        if (token == eos) {
            break;
        }

        char buf[8];
        int len = llama_token_to_piece(
                g_model,
                token,
                buf,
                sizeof(buf),
                0,
                false
        );

        if (len > 0) {
            output.append(buf, len);
        }

        llama_batch batch2 = llama_batch_init(1, 0, 1);
        batch2.token[0] = token;
        batch2.pos[0]   = tokens.size() + i;
        batch2.seq_id[0][0] = 0;
        batch2.n_seq_id[0]  = 1;
        batch2.logits[0]    = true;
        batch2.n_tokens     = 1;

        llama_decode(g_ctx, batch2);
        llama_batch_free(batch2);
    }

    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, text);

    return env->NewStringUTF(output.c_str());
}

// ------------------------------------------------------------
// Free resources
// ------------------------------------------------------------
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_freeModel(
        JNIEnv * /* env */,
        jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    llama_backend_free();
    LOGI("Model freed");
}

} // extern "C"