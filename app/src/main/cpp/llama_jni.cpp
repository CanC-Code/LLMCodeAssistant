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

// Helper to add tokens to a batch
static void common_batch_add(struct llama_batch & batch, llama_token id, llama_pos pos, const std::vector<llama_seq_id> & seq_ids, bool logits) {
    batch.token[batch.n_tokens] = id;
    batch.pos[batch.n_tokens]   = pos;
    batch.n_seq_id[batch.n_tokens] = seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); ++i) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits[batch.n_tokens] = logits;
    batch.n_tokens++;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv *env, jobject /*thiz*/, jstring model_path, jint n_ctx) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    // Enable mmap for faster loading on Android
    mparams.use_mmap = true; 
    
    model = llama_model_load_from_file(path, mparams);

    if (!model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx;
    cparams.n_batch = 512; // Ensure batch size is defined
    ctx = llama_init_from_model(model, cparams);

    if (!ctx) {
        LOGE("Failed to create llama context");
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    // Initialize sampler with greedy strategy
    sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    LOGI("Model loaded successfully: %s", path);
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv *env, jobject /*thiz*/, jstring prompt, jint max_tokens, jobject callback) {
    if (!ctx || !model) {
        LOGE("generateNative: model or context is null");
        return;
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "(Ljava/lang/String;)V");

    const char *prompt_raw = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    
    // 1. Format prompt with system rules
    std::string formatted_prompt = system_rules.empty() ? std::string(prompt_raw) : system_rules + "\n" + prompt_raw;
    env->ReleaseStringUTFChars(prompt, prompt_raw);

    // 2. Tokenize
    std::vector<llama_token> tokens_list;
    int n_tokens_req = -llama_tokenize(vocab, formatted_prompt.c_str(), (int)formatted_prompt.length(), NULL, 0, true, true);
    tokens_list.resize(n_tokens_req);
    llama_tokenize(vocab, formatted_prompt.c_str(), (int)formatted_prompt.length(), tokens_list.data(), (int)tokens_list.size(), true, true);

    // 3. Clear KV cache for new request (prevent context mixing)
    llama_kv_cache_clear(ctx);

    // 4. Process Prompt
    llama_batch batch = llama_batch_init(512, 0, 1);
    for (size_t i = 0; i < tokens_list.size(); i++) {
        common_batch_add(batch, tokens_list[i], (llama_pos)i, {0}, (i == tokens_list.size() - 1));
    }

    if (llama_decode(ctx, batch)) {
        LOGE("Failed to decode prompt tokens");
        llama_batch_free(batch);
        return;
    }

    // 5. Generate loop
    std::string full_response = "";
    llama_pos n_cur = (llama_pos)tokens_list.size();

    for (int i = 0; i < max_tokens; i++) {
        const llama_token id = llama_sampler_sample(sampler, ctx, -1);
        
        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[256];
        int n_chars = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n_chars > 0) {
            std::string piece(buf, n_chars);
            full_response += piece;
            
            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        // Prepare next token
        batch.n_tokens = 0;
        common_batch_add(batch, id, n_cur++, {0}, true);
        
        if (llama_decode(ctx, batch)) {
            LOGE("Failed to decode token at pos %d", (int)n_cur);
            break;
        }
    }

    jstring jfull = env->NewStringUTF(full_response.c_str());
    env->CallVoidMethod(callback, onCompleteMethod, jfull);
    env->DeleteLocalRef(jfull);

    llama_batch_free(batch);
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_clearHistoryNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (ctx) llama_kv_cache_clear(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (sampler) { llama_sampler_free(sampler); sampler = nullptr; }
    if (ctx)     { llama_free(ctx);             ctx     = nullptr; }
    if (model)   { llama_model_free(model);     model   = nullptr; }
    llama_backend_free();
}
