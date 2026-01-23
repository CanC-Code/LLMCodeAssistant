// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI bridge for llama.cpp with Mistral chat template and model rules

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

// Apply Mistral chat template with system rules
static std::string apply_mistral_template(const std::string& user_prompt) {
    std::ostringstream oss;
    
    // Add system rules if present
    if (!g_model_rules.empty()) {
        oss << "[INST] " << g_model_rules << " [/INST] Understood. I will follow these rules.</s>";
    }
    
    // Add chat history
    for (const auto& turn : g_chat_history) {
        oss << "[INST] " << turn.first << " [/INST] " << turn.second << "</s>";
    }
    
    // Add current prompt
    oss << "[INST] " << user_prompt << " [/INST]";
    
    return oss.str();
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
    LOGI("Loading model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Model load failed");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = 512;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Context creation failed");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);

    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Model ready (ctx=%d)", nCtx);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(
        JNIEnv * env,
        jobject,
        jstring prompt,
        jint maxTokens) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model || !g_sampler) {
        return env->NewStringUTF("[Error: Model not initialized]");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::string user_input(c_prompt);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    std::string formatted = apply_mistral_template(user_input);
    LOGI("Formatted: %s", formatted.c_str());

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    // Tokenize
    std::vector<llama_token> tokens(formatted.length() + 256);
    int n_tokens = llama_tokenize(vocab, formatted.c_str(), formatted.length(), 
                                   tokens.data(), tokens.size(), true, false);

    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, formatted.c_str(), formatted.length(), 
                                  tokens.data(), tokens.size(), true, false);
    }

    if (n_tokens <= 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("[Error: Tokenization failed]");
    }

    tokens.resize(n_tokens);
    LOGI("Tokenized: %d tokens", n_tokens);

    // Validate context
    int n_ctx = llama_n_ctx(g_ctx);
    if (n_tokens + maxTokens > n_ctx) {
        LOGE("Too long: %d+%d > %d", n_tokens, maxTokens, n_ctx);
        return env->NewStringUTF("[Error: Prompt too long]");
    }

    // Process prompt tokens
    for (int i = 0; i < n_tokens; ++i) {
        llama_batch batch = llama_batch_get_one(&tokens[i], 1, i, 0);
        
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Decode failed at token %d", i);
            return env->NewStringUTF("[Error: Decode failed]");
        }
    }

    LOGI("Prompt processed, generating...");

    // Generate
    std::string output;
    int pos = n_tokens;

    for (int i = 0; i < maxTokens; ++i) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (tok == llama_vocab_eos(vocab) || tok == llama_vocab_eot(vocab)) {
            LOGI("EOS at %d", i);
            break;
        }

        char buf[128];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) {
            output.append(buf, len);
        }

        if (output.find("</s>") != std::string::npos) {
            output = output.substr(0, output.find("</s>"));
            break;
        }

        llama_batch next = llama_batch_get_one(&tok, 1, pos++, 0);
        if (llama_decode(g_ctx, next) != 0) {
            LOGE("Gen decode failed at %d", i);
            break;
        }
    }

    LOGI("Generated: %zu chars", output.length());

    // Trim
    size_t start = output.find_first_not_of(" \n\r\t");
    size_t end = output.find_last_not_of(" \n\r\t");
    if (start != std::string::npos && end != std::string::npos) {
        output = output.substr(start, end - start + 1);
    }

    if (!output.empty()) {
        g_chat_history.push_back({user_input, output});
        if ((int)g_chat_history.size() > g_max_history_turns) {
            g_chat_history.erase(g_chat_history.begin());
        }
    }

    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_setModelRulesNative(
        JNIEnv * env,
        jobject,
        jstring rules) {

    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (rules == nullptr) {
        g_model_rules = "";
    } else {
        const char * c_rules = env->GetStringUTFChars(rules, nullptr);
        g_model_rules = c_rules;
        env->ReleaseStringUTFChars(rules, c_rules);
    }
    
    LOGI("Model rules updated: %s", g_model_rules.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_setMaxHistoryTurnsNative(
        JNIEnv *,
        jobject,
        jint turns) {

    std::lock_guard<std::mutex> lock(g_mutex);
    g_max_history_turns = turns;
    LOGI("Max history turns: %d", turns);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_clearHistoryNative(
        JNIEnv *,
        jobject) {

    std::lock_guard<std::mutex> lock(g_mutex);
    g_chat_history.clear();
    LOGI("Chat history cleared");
}

extern "C"
JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(
        JNIEnv *,
        jobject) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }

    g_chat_history.clear();
    g_model_rules = "";

    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }

    LOGI("Shutdown complete");
}