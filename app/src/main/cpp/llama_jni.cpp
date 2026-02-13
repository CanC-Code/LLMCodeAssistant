#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <cstring>

extern "C" {
#include "llama.h"
}

#define LOG_TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model   * g_model   = nullptr;
static llama_context * g_ctx     = nullptr;
static llama_sampler * g_sampler = nullptr;

static std::vector<std::pair<std::string, std::string>> g_chat_history;
static std::string g_model_rules = "You are a helpful coding assistant.";
static int g_max_history_turns = 3;

// Helper to tokenize text using the stable API
static std::vector<llama_token> tokenize(const struct llama_model * model, const std::string & text, bool add_special, bool parse_special) {
    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    std::vector<llama_token> res(text.length() + 32);
    int n = llama_tokenize(vocab, text.c_str(), (int)text.length(), res.data(), (int)res.size(), add_special, parse_special);
    if (n < 0) {
        res.resize(-n);
        n = llama_tokenize(vocab, text.c_str(), (int)text.length(), res.data(), (int)res.size(), add_special, parse_special);
    }
    res.resize(n);
    return res;
}

// Builds the ChatML prompt string and tokenizes it
static std::vector<llama_token> apply_chatml_template(const std::string& user_prompt, const struct llama_model * model) {
    std::string full_prompt = "<|im_start|>system\n" + g_model_rules + "<|im_end|>\n";
    
    for (const auto& turn : g_chat_history) {
        full_prompt += "<|im_start|>user\n" + turn.first + "<|im_end|>\n";
        full_prompt += "<|im_start|>assistant\n" + turn.second + "<|im_end|>\n";
    }
    
    full_prompt += "<|im_start|>user\n" + user_prompt + "<|im_end|>\n<|im_start|>assistant\n";
    return tokenize(model, full_prompt, true, true);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv * env, jobject, jstring modelPath, jint nCtx) {
    std::lock_guard<std::mutex> lock(g_mutex);

    // 1. Backend and Cleanup
    static bool backend_initialized = false;
    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
    }

    if (g_sampler) llama_sampler_free(g_sampler);
    if (g_ctx)     llama_free(g_ctx);
    if (g_model)   llama_model_free(g_model);

    // 2. Load Model
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU focus for stability
    
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!g_model) return JNI_FALSE;

    // 3. Init Context
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;
    
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) return JNI_FALSE;

    // 4. Init Sampler
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv * env, jobject, jstring prompt, jint maxTokens, jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model) return;

    // Setup Java Callback IDs
    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::string user_input(c_prompt);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    // Reset KV cache for fresh generation
    llama_kv_cache_seq_rm(g_ctx, -1, -1, -1);

    std::vector<llama_token> tokens = apply_chatml_template(user_input, g_model);
    if (tokens.empty()) return;

    // Prefill Phase
    llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);
    for (int i = 0; i < (int)tokens.size(); i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == (int)tokens.size() - 1);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return;
    }
    
    int n_past = batch.n_tokens;
    llama_batch_free(batch);

    // Generation Phase
    std::string full_output;
    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);

    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        
        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) {
            std::string piece(buf, len);
            if (piece.find("<|im_end|>") != std::string::npos) break;

            full_output += piece;
            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onToken, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        // Prepare next token
        llama_batch next = llama_batch_get_one(&tok, 1);
        next.pos[0] = n_past++;
        if (llama_decode(g_ctx, next) != 0) break;
    }

    // Update History
    g_chat_history.push_back({user_input, full_output});
    if (g_chat_history.size() > g_max_history_turns) g_chat_history.erase(g_chat_history.begin());

    env->CallVoidMethod(callback, onComplete, env->NewStringUTF(full_output.c_str()));
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_sampler) llama_sampler_free(g_sampler);
    if (g_ctx)     llama_free(g_ctx);
    if (g_model)   llama_model_free(g_model);
    g_sampler = nullptr; g_ctx = nullptr; g_model = nullptr;
}
