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

// Helper to add tokens to a batch with alignment to your header's llama_batch struct
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv *env, jobject /*thiz*/, jstring model_path, jint n_ctx) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    
    // Modern llama.cpp uses no-arg backend init
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    model = llama_model_load_from_file(path, mparams);

    if (!model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx   = n_ctx;
    cparams.n_batch = 512; // Matches common Android hardware constraints
    
    ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to initialize context");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    // Initialize the sampler chain as per your header's chain logic
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
    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    // Context construction
    std::string formatted_prompt = system_rules.empty() ? prompt_str : system_rules + "\n" + prompt_str;

    // Tokenize using the vocab pointer (Modern API)
    std::vector<llama_token> tokens_list;
    int n_tokens_req = -llama_tokenize(vocab, formatted_prompt.c_str(), (int)formatted_prompt.length(), NULL, 0, true, true);
    tokens_list.resize(n_tokens_req);
    llama_tokenize(vocab, formatted_prompt.c_str(), (int)formatted_prompt.length(), tokens_list.data(), (int)tokens_list.size(), true, true);

    std::string full_response = "";
    // Initialize batch for sequence 0
    llama_batch batch = llama_batch_init(512, 0, 1);

    // Load prompt into batch
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

        // Sample the next token
        const llama_token id = llama_sampler_sample(sampler, ctx, -1);
        
        // Check for End of Generation using vocab pointer
        if (llama_vocab_is_eog(vocab, id)) break;

        // Convert token to piece (text)
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
        // PER YOUR LLAMA.H:
        // llama_kv_cache_seq_rm(ctx, seq_id, p0, p1)
        // -1 for seq_id = all sequences
        // 0 for p0 = from start
        // -1 for p1 = to end
        llama_kv_cache_seq_rm(ctx, -1, 0, -1);
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
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }
    llama_backend_free();
}
