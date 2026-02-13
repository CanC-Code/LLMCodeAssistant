#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <sstream>
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

// Chat history
static std::vector<std::pair<std::string, std::string>> g_chat_history;

// Model rules (system prompt)
static std::string g_model_rules = "";

// Settings
static int g_max_history_turns = 3;

/**
 * Apply ChatML template for Qwen2.5
 * Format: <|im_start|>role\ncontent<|im_end|>\n
 */
static std::vector<llama_token> apply_chatml_template(const std::string& user_prompt, const struct llama_model * model) {
    std::vector<llama_token> tokens;
    const auto * vocab = llama_model_get_vocab(model);

    // Qwen2.5 typically doesn't use a BOS token in ChatML, 
    // but llama_tokenize with parse_special=true handles control tokens.
    auto tokenize = [&tokens, vocab](const std::string& text, bool add_special = false, bool parse_special = true) {
        std::vector<llama_token> res(text.length() + 32); // Extra padding for special tokens
        int n = llama_tokenize(vocab, text.c_str(), text.length(), res.data(), res.size(), add_special, parse_special);
        if (n < 0) {
            res.resize(-n);
            n = llama_tokenize(vocab, text.c_str(), text.length(), res.data(), res.size(), add_special, parse_special);
        }
        res.resize(n);
        tokens.insert(tokens.end(), res.begin(), res.end());
    };

    // 1. System Prompt
    std::string sys_msg = g_model_rules.empty() ? "You are a helpful coding assistant." : g_model_rules;
    tokenize("<|im_start|>system\n" + sys_msg + "<|im_end|>\n");

    // 2. Chat History
    for (const auto& turn : g_chat_history) {
        tokenize("<|im_start|>user\n" + turn.first + "<|im_end|>\n");
        tokenize("<|im_start|>assistant\n" + turn.second + "<|im_end|>\n");
    }
    
    // 3. Current Prompt
    tokenize("<|im_start|>user\n" + user_prompt + "<|im_end|>\n<|im_start|>assistant\n");

    return tokens;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(
        JNIEnv * env,
        jobject,
        jstring modelPath,
        jint nCtx) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }
    
    g_chat_history.clear();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading Qwen2.5 Model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU inference for Android
    
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Model load failed - check file path and permissions");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = 512;
    cparams.n_threads = 4; // Adjusted for mobile thermals
    cparams.n_threads_batch = 4;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Context creation failed");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Modern Sampler Chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);

    // Order matters: Temp -> Top-K -> Top-P -> Min-P
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1)); 
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Qwen ready (context: %d)", nCtx);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(
        JNIEnv * env,
        jobject thiz,
        jstring prompt,
        jint maxTokens,
        jobject callback) {

    std::lock_guard<std::mutex> lock(g_mutex);

    jclass cls = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onErrorMethod = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");

    if (!g_ctx || !g_model || !g_sampler) {
        jstring err = env->NewStringUTF("[Error: Model not initialized]");
        env->CallVoidMethod(callback, onErrorMethod, err);
        return;
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::string user_input(c_prompt);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    // 1. Use Qwen-compatible ChatML template
    std::vector<llama_token> tokens = apply_chatml_template(user_input, g_model);
    int n_tokens = tokens.size();

    // 2. Validate Context space
    int n_ctx = llama_n_ctx(g_ctx);
    if (n_tokens + maxTokens > n_ctx) {
        jstring err = env->NewStringUTF("[Error: Context limit reached. Clear history.]");
        env->CallVoidMethod(callback, onErrorMethod, err);
        return;
    }

    // 3. Initialize KV cache with prompt
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int j = 0; j < n_tokens; ++j) {
        batch.token[j] = tokens[j];
        batch.pos[j] = j;
        batch.n_seq_id[j] = 1;
        batch.seq_id[j][0] = 0;
    }
    batch.logits[n_tokens - 1] = 1; // Logits only needed for the very last token

    if (llama_decode(g_ctx, batch) != 0) {
        jstring err = env->NewStringUTF("[Error: Decode failed]");
        env->CallVoidMethod(callback, onErrorMethod, err);
        llama_batch_free(batch);
        return;
    }
    llama_batch_free(batch);

    // 4. Generation Loop
    std::string full_output;
    int pos = n_tokens;
    const auto * vocab = llama_model_get_vocab(g_model);
    const auto eos_token = llama_vocab_eos(vocab);

    for (int i = 0; i < maxTokens; ++i) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Qwen EOG Check
        if (tok == eos_token) break;

        char buf[128];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) {
            std::string piece(buf, len);
            
            // Safety: ChatML models sometimes hallucinate the stop tag as a string
            if (piece.find("<|im_end|>") != std::string::npos) break;

            full_output += piece;

            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        // Prepare next token
        llama_batch next = llama_batch_init(1, 0, 1);
        next.n_tokens = 1;
        next.token[0] = tok;
        next.pos[0] = pos;
        next.n_seq_id[0] = 1;
        next.seq_id[0][0] = 0;
        next.logits[0] = 1;

        if (llama_decode(g_ctx, next) != 0) {
            jstring err = env->NewStringUTF("[Error: Streaming decode failed]");
            env->CallVoidMethod(callback, onErrorMethod, err);
            llama_batch_free(next);
            return;
        }
        llama_batch_free(next);
        pos++;
    }

    // 5. Save to history (trimmed)
    size_t start = full_output.find_first_not_of(" \n\r\t");
    size_t end = full_output.find_last_not_of(" \n\r\t");
    std::string cleaned = (start == std::string::npos) ? "" : full_output.substr(start, end - start + 1);

    if (!cleaned.empty()) {
        g_chat_history.push_back({user_input, cleaned});
        if ((int)g_chat_history.size() > g_max_history_turns) {
            g_chat_history.erase(g_chat_history.begin());
        }
    }

    jstring full = env->NewStringUTF(cleaned.c_str());
    env->CallVoidMethod(callback, onCompleteMethod, full);
    env->DeleteLocalRef(full);
}

// ... (Rest of history/shutdown functions remain same but use g_mutex) ...

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_setModelRulesNative(JNIEnv * env, jobject, jstring rules) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (rules == nullptr) { g_model_rules = ""; } 
    else {
        const char * c_rules = env->GetStringUTFChars(rules, nullptr);
        g_model_rules = c_rules;
        env->ReleaseStringUTFChars(rules, c_rules);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_clearHistoryNative(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_chat_history.clear();
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }
    if (g_backend_initialized) { llama_backend_free(); g_backend_initialized = false; }
}
