#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

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
static bool g_backend_initialized = false;

static std::vector<std::pair<std::string, std::string>> g_chat_history;
static std::string g_model_rules = "";
static int g_max_history_turns = 3;

// Helper to tokenize text with ChatML support
static std::vector<llama_token> tokenize(const struct llama_model * model, const std::string & text, bool add_special, bool parse_special) {
    const auto * vocab = llama_model_get_vocab(model);
    std::vector<llama_token> res(text.length() + 32);
    int n = llama_tokenize(vocab, text.c_str(), text.length(), res.data(), res.size(), add_special, parse_special);
    if (n < 0) {
        res.resize(-n);
        n = llama_tokenize(vocab, text.c_str(), text.length(), res.data(), res.size(), add_special, parse_special);
    }
    res.resize(n);
    return res;
}

static std::vector<llama_token> apply_chatml_template(const std::string& user_prompt, const struct llama_model * model) {
    std::vector<llama_token> tokens;
    
    // 1. System Prompt
    std::string sys_msg = g_model_rules.empty() ? "You are a helpful coding assistant." : g_model_rules;
    auto sys_tokens = tokenize(model, "<|im_start|>system\n" + sys_msg + "<|im_end|>\n", false, true);
    tokens.insert(tokens.end(), sys_tokens.begin(), sys_tokens.end());

    // 2. Chat History
    for (const auto& turn : g_chat_history) {
        auto u_toks = tokenize(model, "<|im_start|>user\n" + turn.first + "<|im_end|>\n", false, true);
        auto a_toks = tokenize(model, "<|im_start|>assistant\n" + turn.second + "<|im_end|>\n", false, true);
        tokens.insert(tokens.end(), u_toks.begin(), u_toks.end());
        tokens.insert(tokens.end(), a_toks.begin(), a_toks.end());
    }

    // 3. Current Prompt
    auto p_toks = tokenize(model, "<|im_start|>user\n" + user_prompt + "<|im_end|>\n<|im_start|>assistant\n", false, true);
    tokens.insert(tokens.end(), p_toks.begin(), p_toks.end());

    return tokens;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv * env, jobject, jstring modelPath, jint nCtx) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) llama_sampler_free(g_sampler);
    if (g_ctx)     llama_free(g_ctx);
    if (g_model)   llama_model_free(g_model);
    
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; 
    
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) return JNI_FALSE;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = 512;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) return JNI_FALSE;

    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv * env, jobject thiz, jstring prompt, jint maxTokens, jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);

    jclass cls = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");

    if (!g_ctx) {
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Model not initialized"));
        return;
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::string user_input(c_prompt);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    // Clear KV cache for fresh start (simplest for mobile memory management)
    llama_kv_cache_clear(g_ctx);

    std::vector<llama_token> tokens = apply_chatml_template(user_input, g_model);
    
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        llama_batch_add(batch, tokens[i], i, {0}, i == tokens.size() - 1);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Decode failed"));
        llama_batch_free(batch);
        return;
    }

    std::string full_output;
    int n_cur = batch.n_tokens;
    llama_batch_free(batch);

    const auto * vocab = llama_model_get_vocab(g_model);
    const auto eos_token = llama_vocab_eos(vocab);

    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        if (tok == eos_token) break;

        char buf[128];
        int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            if (piece.find("<|im_end|>") != std::string::npos) break;
            
            full_output += piece;
            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onToken, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        batch = llama_batch_get_one(&tok, 1);
        batch.pos[0] = n_cur++;
        if (llama_decode(g_ctx, batch) != 0) break;
    }

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
    if (g_backend_initialized) llama_backend_free();
    g_sampler = nullptr; g_ctx = nullptr; g_model = nullptr;
    g_backend_initialized = false;
}
