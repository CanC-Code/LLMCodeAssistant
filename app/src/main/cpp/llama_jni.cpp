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
static std::string system_rules = "";

[span_1](start_span)// Helper to add tokens to a batch with alignment to your header's llama_batch struct[span_1](end_span)
static void common_batch_add(struct llama_batch & batch, llama_token id, llama_pos pos, const std::vector<llama_seq_id> & seq_ids, bool logits) {
    batch.token[batch.n_tokens] = id;
    batch.pos[batch.n_tokens]   = pos;
    batch.n_seq_id[batch.n_tokens] = seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); ++i) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits[batch.n_tokens] = logits ? 1 : 0;
    batch.n_tokens++;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv *env, jobject /*thiz*/, jstring model_path, jint n_ctx) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);

    [span_2](start_span)// Initializing backend[span_2](end_span)
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    [span_3](start_span)model = llama_model_load_from_file(path, mparams);[span_3](end_span)

    if (!model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    [span_4](start_span)llama_context_params cparams = llama_context_default_params();[span_4](end_span)
    cparams.n_ctx   = n_ctx;
    cparams.n_batch = 512; 

    [span_5](start_span)ctx = llama_init_from_model(model, cparams);[span_5](end_span)
    if (!ctx) {
        LOGE("Failed to initialize context");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    [span_6](start_span)// Sampler initialization as per llama.h chain logic[span_6](end_span)
    sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv *env, jobject /*thiz*/, jstring prompt, jint max_tokens, jobject callback) {
    if (!ctx || !model || !sampler) return;

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "(Ljava/lang/String;)V");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    [span_7](start_span)const struct llama_vocab * vocab = llama_model_get_vocab(model);[span_7](end_span)

    std::string formatted_prompt = system_rules.empty() ? prompt_str : system_rules + "\n" + prompt_str;

    [span_8](start_span)// Tokenization using the vocab pointer[span_8](end_span)
    std::vector<llama_token> tokens_list;
    int n_tokens_req = -llama_tokenize(vocab, formatted_prompt.c_str(), (int)formatted_prompt.length(), NULL, 0, true, true);
    tokens_list.resize(n_tokens_req);
    llama_tokenize(vocab, formatted_prompt.c_str(), (int)formatted_prompt.length(), tokens_list.data(), (int)tokens_list.size(), true, true);

    std::string full_response = "";
    llama_batch batch = llama_batch_init(512, 0, 1);

    for (size_t i = 0; i < tokens_list.size(); i++) {
        common_batch_add(batch, tokens_list[i], (llama_pos)i, {0}, (i == tokens_list.size() - 1));
    }

    llama_pos n_cur = (llama_pos)tokens_list.size();
    int n_decode = 0;

    while (n_decode < max_tokens) {
        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode failed");
            break;
        }

        const llama_token id = llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[128];
        int n_chars = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n_chars > 0) {
            std::string piece(buf, n_chars);
            full_response += piece;
            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        batch.n_tokens = 0;
        common_batch_add(batch, id, n_cur, {0}, true);

        n_cur++;
        n_decode++;
    }

    jstring jfull = env->NewStringUTF(full_response.c_str());
    env->CallVoidMethod(callback, onCompleteMethod, jfull);

    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, prompt_str);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_clearHistoryNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (ctx) {
        // CORRECTED FOR YOUR LLAMA.H VERSION:
        [span_9](start_span)// Use llama_get_memory and llama_memory_clear[span_9](end_span)
        llama_memory_t mem = llama_get_memory(ctx);
        if (mem) {
            // Clearing the memory sequence history
            llama_memory_clear(mem, true);
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_setModelRulesNative(JNIEnv *env, jobject /*thiz*/, jstring rules) {
    const char *rules_str = env->GetStringUTFChars(rules, nullptr);
    system_rules = std::string(rules_str);
    env->ReleaseStringUTFChars(rules, rules_str);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (sampler) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }
    if (ctx) {
        [span_10](start_span)llama_free(ctx);[span_10](end_span)
        ctx = nullptr;
    }
    if (model) {
        [span_11](start_span)llama_model_free(model);[span_11](end_span)
        model = nullptr;
    }
    [span_12](start_span)llama_backend_free();[span_12](end_span)
}
