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

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv *env, jobject thiz, jstring model_path, jint n_ctx) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    model = llama_model_load_from_file(path, mparams);

    if (!model) {
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx;
    cparams.n_batch = 512;
    cparams.n_threads = 4;

    ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv *env, jobject thiz, jstring prompt, jint max_tokens, jobject callback) {
    if (!ctx || !model) return;

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "(Ljava/lang/String;)V");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    std::string formatted_prompt = system_rules + "\nUser: " + prompt_str + "\nAssistant: ";
    
    // Modern Tokenization
    std::vector<llama_token> tokens_list;
    int n_tokens = -llama_tokenize(vocab, formatted_prompt.c_str(), formatted_prompt.length(), NULL, 0, true, true);
    tokens_list.resize(n_tokens);
    llama_tokenize(vocab, formatted_prompt.c_str(), formatted_prompt.length(), tokens_list.data(), tokens_list.size(), true, true);

    std::string full_response = "";
    // Initialize batch manually as llama_batch_add is missing in your environment
    llama_batch batch = llama_batch_init(512, 0, 1);

    for (size_t i = 0; i < tokens_list.size(); i++) {
        batch.token[batch.n_tokens] = tokens_list[i];
        batch.pos[batch.n_tokens] = i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = (i == tokens_list.size() - 1);
        batch.n_tokens++;
    }

    int n_cur = tokens_list.size();
    int n_decode = 0;

    while (n_decode < max_tokens) {
        if (llama_decode(ctx, batch)) break;

        const llama_token id = llama_sampler_sample(sampler, ctx, -1);
        
        [span_2](start_span)// Use updated EOG check[span_2](end_span)
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

        // Prepare batch for next single token
        batch.n_tokens = 0;
        batch.token[0] = id;
        batch.pos[0] = n_cur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        batch.n_tokens = 1;

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
Java_io_canccode_aca_LlamaBridge_clearHistoryNative(JNIEnv *env, jobject thiz) {
    if (ctx) {
        [span_3](start_span)// Correct way to clear KV cache in modern versions[span_3](end_span)
        llama_kv_cache_clear(ctx); 
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_setModelRulesNative(JNIEnv *env, jobject thiz, jstring rules) {
    const char *rules_str = env->GetStringUTFChars(rules, nullptr);
    system_rules = std::string(rules_str);
    env->ReleaseStringUTFChars(rules, rules_str);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv *env, jobject thiz) {
    if (sampler) llama_sampler_free(sampler);
    if (ctx) llama_free(ctx);
    if (model) llama_model_free(model);
    llama_backend_free();
}
