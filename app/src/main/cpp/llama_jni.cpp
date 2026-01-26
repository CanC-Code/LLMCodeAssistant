#include <jni.h>
#include <string>
#include <mutex>
#include <vector>
#include <android/log.h>

// Include the public llama.cpp API
#include "llama.h"

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_sampler * g_sampler = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv * env, jobject, jstring modelPath, jint nCtx) {
    [span_4](start_span)std::lock_guard<std::mutex> lock(g_mutex);[span_4](end_span)

    [span_5](start_span)const char * path = env->GetStringUTFChars(modelPath, nullptr);[span_5](end_span)

    // Initialize model
    [span_6](start_span)llama_model_params mparams = llama_model_default_params();[span_6](end_span)
    [span_7](start_span)g_model = llama_model_load_from_file(path, mparams);[span_7](end_span)

    if (!g_model) {
        LOGE("Failed to load model: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        [span_8](start_span)return JNI_FALSE;[span_8](end_span)
    }

    // Initialize context
    [span_9](start_span)llama_context_params cparams = llama_context_default_params();[span_9](end_span)
    [span_10](start_span)cparams.n_ctx = nCtx;[span_10](end_span)
    [span_11](start_span)g_ctx = llama_init_from_model(g_model, cparams);[span_11](end_span)

    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        [span_12](start_span)return JNI_FALSE;[span_12](end_span)
    }

    // Modern Sampler initialization (Chain API)
    [span_13](start_span)g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());[span_13](end_span)
    [span_14](start_span)llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.8f));[span_14](end_span)
    [span_15](start_span)llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));[span_15](end_span)
    [span_16](start_span)llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));[span_16](end_span)
    [span_17](start_span)llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));[span_17](end_span)

    [span_18](start_span)env->ReleaseStringUTFChars(modelPath, path);[span_18](end_span)
    LOGI("Llama initialized successfully");
    [span_19](start_span)return JNI_TRUE;[span_19](end_span)
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv * env, jobject, jstring prompt, jint maxTokens, jobject callback) {
    [span_20](start_span)std::lock_guard<std::mutex> lock(g_mutex);[span_20](end_span)

    [span_21](start_span)if (!g_ctx || !g_model) return;[span_21](end_span)

    [span_22](start_span)// Clear KV cache for a fresh generation[span_22](end_span)
    [span_23](start_span)llama_kv_cache_seq_rm(g_ctx, (llama_seq_id)-1, -1, -1);[span_23](end_span)

    [span_24](start_span)const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);[span_24](end_span)
    [span_25](start_span)const struct llama_vocab * vocab = llama_model_get_vocab(g_model);[span_25](end_span)

    // Tokenize prompt
    [span_26](start_span)std::vector<llama_token> tokens(strlen(c_prompt) + 1);[span_26](end_span)
    [span_27](start_span)int n_tokens = llama_tokenize(vocab, c_prompt, strlen(c_prompt), tokens.data(), tokens.size(), true, false);[span_27](end_span)
    [span_28](start_span)tokens.resize(n_tokens);[span_28](end_span)
    [span_29](start_span)env->ReleaseStringUTFChars(prompt, c_prompt);[span_29](end_span)

    // Prepare batch for initial prompt
    [span_30](start_span)llama_batch batch = llama_batch_init(tokens.size(), 0, 1);[span_30](end_span)
    for (int i = 0; i < (int)tokens.size(); i++) {
        [span_31](start_span)batch.token[i] = tokens[i];[span_31](end_span)
        [span_32](start_span)batch.pos[i] = i;[span_32](end_span)
        [span_33](start_span)batch.n_seq_id[i] = 1;[span_33](end_span)
        [span_34](start_span)batch.seq_id[i][0] = 0;[span_34](end_span)
        [span_35](start_span)batch.logits[i] = false;[span_35](end_span)
    }
    [span_36](start_span)batch.logits[tokens.size() - 1] = true;[span_36](end_span)

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        [span_37](start_span)return;[span_37](end_span)
    }
    [span_38](start_span)llama_batch_free(batch);[span_38](end_span)

    // Get Java callback method
    [span_39](start_span)jclass cls = env->GetObjectClass(callback);[span_39](end_span)
    [span_40](start_span)jmethodID onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");[span_40](end_span)

    // Autoregressive generation loop
    [span_41](start_span)int n_past = tokens.size();[span_41](end_span)
    for (int i = 0; i < maxTokens; i++) {
        [span_42](start_span)// Sample next token[span_42](end_span)
        [span_43](start_span)llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);[span_43](end_span)

        // Check for End-of-Generation
        [span_44](start_span)if (llama_vocab_is_eog(vocab, tok)) break;[span_44](end_span)

        // Convert token to string piece
        [span_45](start_span)char buf[128];[span_45](end_span)
        [span_46](start_span)int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);[span_46](end_span)
        if (len > 0) {
            [span_47](start_span)env->CallVoidMethod(callback, onToken, env->NewStringUTF(std::string(buf, len).c_str()));[span_47](end_span)
        }

        // Decode the single sampled token
        [span_48](start_span)llama_batch next = llama_batch_get_one(&tok, 1);[span_48](end_span)
        [span_49](start_span)next.pos[0] = n_past;[span_49](end_span)
        [span_50](start_span)if (llama_decode(g_ctx, next) != 0) break;[span_50](end_span)
        [span_51](start_span)n_past++;[span_51](end_span)
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv *, jobject) {
    [span_52](start_span)std::lock_guard<std::mutex> lock(g_mutex);[span_52](end_span)
    [span_53](start_span)if (g_sampler) llama_sampler_free(g_sampler);[span_53](end_span)
    [span_54](start_span)if (g_ctx) llama_free(g_ctx);[span_54](end_span)
    [span_55](start_span)if (g_model) llama_model_free(g_model);[span_55](end_span)
    [span_56](start_span)g_sampler = nullptr; g_ctx = nullptr; g_model = nullptr;[span_56](end_span)
}
