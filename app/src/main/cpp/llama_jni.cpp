#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <mutex>
#include "llama.h"

#define TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model * model = nullptr;
static llama_context * ctx = nullptr;
static llama_sampler * sampler = nullptr;
static std::string system_rules = "";
static std::mutex g_mutex;

// Track current position in the KV cache to allow for multi-turn chat
static int32_t n_past = 0;

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
    std::lock_guard<std::mutex> lock(g_mutex);
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    // Enable GPU/Vulkan support if your NDK build supports it
    mparams.n_gpu_layers = 32; 

    model = llama_model_load_from_file(path, mparams);
    if (!model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx;
    cparams.n_batch = 512;
    ctx = llama_init_from_model(model, cparams);

    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    // Initialize sampler with common defaults for creative/coding tasks
    sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    n_past = 0; // Reset cache position
    LOGI("Model loaded successfully. Context size: %d", n_ctx);
    
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv *env, jobject /*thiz*/, jstring prompt, jint max_tokens, jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!ctx || !model) {
        LOGE("generateNative: Model not initialized");
        return;
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "(Ljava/lang/String;)V");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    // 1. Better Prompt Formatting (Simplified for general GGUF)
    std::string formatted_prompt;
    if (n_past == 0 && !system_rules.empty()) {
        formatted_prompt = "System: " + system_rules + "\nUser: " + prompt_str + "\nAssistant: ";
    } else {
        formatted_prompt = "User: " + std::string(prompt_str) + "\nAssistant: ";
    }

    // 2. Tokenization
    std::vector<llama_token> tokens_list;
    int n_tokens_req = -llama_tokenize(vocab, formatted_prompt.c_str(), (int)formatted_prompt.length(), NULL, 0, true, true);
    tokens_list.resize(n_tokens_req);
    llama_tokenize(vocab, formatted_prompt.c_str(), (int)formatted_prompt.length(), tokens_list.data(), (int)tokens_list.size(), true, true);

    // 3. Prevent Context Overflow
    uint32_t n_ctx = llama_n_ctx(ctx);
    if (n_past + tokens_list.size() > n_ctx) {
        LOGI("Context full, clearing KV cache");
        llama_kv_cache_clear(ctx);
        n_past = 0;
    }

    // 4. Processing the Prompt Batch
    llama_batch batch = llama_batch_init(tokens_list.size(), 0, 1);
    for (size_t i = 0; i < tokens_list.size(); i++) {
        common_batch_add(batch, tokens_list[i], n_past++, {0}, (i == tokens_list.size() - 1));
    }

    std::string full_response = "";
    
    // 5. Generation Loop
    for (int i = 0; i < max_tokens; i++) {
        if (llama_decode(ctx, batch)) {
            LOGE("llama_decode failed");
            break;
        }

        const llama_token id = llama_sampler_sample(sampler, ctx, -1);
        
        if (llama_vocab_is_eog(vocab, id)) {
            LOGI("EOS reached");
            break;
        }

        char buf[128];
        int n_chars = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n_chars > 0) {
            std::string piece(buf, n_chars);
            full_response += piece;
            
            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        // Prepare next token for decoding
        batch.n_tokens = 0;
        common_batch_add(batch, id, n_past++, {0}, true);
        
        if (n_past >= n_ctx) break; 
    }

    jstring jfull = env->NewStringUTF(full_response.c_str());
    env->CallVoidMethod(callback, onCompleteMethod, jfull);
    env->DeleteLocalRef(jfull);

    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, prompt_str);
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_clearHistoryNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (ctx) {
        llama_kv_cache_clear(ctx);
        n_past = 0;
        LOGI("KV cache cleared and history reset.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (sampler) { llama_sampler_free(sampler); sampler = nullptr; }
    if (ctx)     { llama_free(ctx);             ctx     = nullptr; }
    if (model)   { llama_model_free(model);     model   = nullptr; }
    llama_backend_free();
    n_past = 0;
}
