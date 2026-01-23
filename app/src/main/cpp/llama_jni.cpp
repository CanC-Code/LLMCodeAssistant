// File: app/src/main/cpp/llama_jni.cpp
// Author: CCVO
// Purpose: JNI bridge for llama.cpp with proper Mistral chat template support

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

// Chat history for multi-turn conversations
static std::vector<std::pair<std::string, std::string>> g_chat_history;

// Apply Mistral Instruct chat template
static std::string apply_mistral_template(const std::string& user_prompt, bool include_history = true) {
    std::ostringstream oss;
    
    // Add chat history if enabled
    if (include_history && !g_chat_history.empty()) {
        for (const auto& turn : g_chat_history) {
            oss << "[INST] " << turn.first << " [/INST] " << turn.second << "</s>";
        }
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

    // Cleanup existing resources
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }
    
    // Clear chat history
    g_chat_history.clear();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only for Android
    
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

    // Setup sampler chain with Mistral-friendly settings
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);

    // Mistral works well with these settings
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Model + context + sampler ready (ctx_size=%d)", nCtx);
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
        LOGE("Model not initialized");
        return env->NewStringUTF("[Error: Model not initialized]");
    }

    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::string user_input(c_prompt);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    // Apply Mistral chat template
    std::string formatted_prompt = apply_mistral_template(user_input, true);
    
    LOGI("User input: %s", user_input.c_str());
    LOGI("Formatted prompt length: %zu chars", formatted_prompt.length());

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    // Tokenize the formatted prompt
    std::vector<llama_token> tokens;
    tokens.resize(formatted_prompt.length() + 256);
    
    int n = llama_tokenize(
        vocab, 
        formatted_prompt.c_str(), 
        formatted_prompt.length(), 
        tokens.data(), 
        tokens.size(), 
        true,  // add_bos
        false  // special tokens
    );

    if (n < 0) {
        LOGE("Tokenization failed: buffer too small");
        tokens.resize(-n);
        n = llama_tokenize(
            vocab, 
            formatted_prompt.c_str(), 
            formatted_prompt.length(), 
            tokens.data(), 
            tokens.size(), 
            true,
            false
        );
    }

    if (n <= 0) {
        LOGE("Tokenization failed completely");
        return env->NewStringUTF("[Error: Tokenization failed]");
    }

    tokens.resize(n);
    LOGI("Prompt tokenized: %d tokens", n);

    // Check context size
    int n_ctx = llama_n_ctx(g_ctx);
    if (n + maxTokens > n_ctx) {
        LOGE("Prompt + max_tokens (%d + %d = %d) exceeds context size (%d)", 
             n, maxTokens, n + maxTokens, n_ctx);
        return env->NewStringUTF("[Error: Prompt too long for context window]");
    }

    // Initialize batch with proper size
    llama_batch batch = llama_batch_init(std::min(n, 512), 0, 1);
    
    // Process prompt in chunks if needed
    for (int i = 0; i < n; i += batch.n_tokens) {
        int batch_size = std::min(batch.n_tokens, n - i);
        
        // Clear batch
        batch.n_tokens = batch_size;
        
        for (int j = 0; j < batch_size; ++j) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = i + j;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = (i + j == n - 1) ? 1 : 0;
        }

        int decode_result = llama_decode(g_ctx, batch);
        if (decode_result != 0) {
            llama_batch_free(batch);
            LOGE("Failed to decode batch at position %d (result: %d)", i, decode_result);
            return env->NewStringUTF("[Error: Failed to process prompt]");
        }
    }
    
    llama_batch_free(batch);
    LOGI("Prompt processing complete, starting generation");

    // Generate response
    std::string output;
    int pos = n;
    int generated = 0;

    for (int i = 0; i < maxTokens; ++i) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Check for EOS
        if (tok == llama_vocab_eos(vocab) || tok == llama_vocab_eot(vocab)) {
            LOGI("EOS/EOT encountered at token %d", i);
            break;
        }

        // Decode token to text
        char buf[128];
        int l = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (l > 0) {
            output.append(buf, l);
            generated++;
        }

        // Check for </s> marker in output
        if (output.find("</s>") != std::string::npos) {
            size_t eos_pos = output.find("</s>");
            output = output.substr(0, eos_pos);
            LOGI("Found </s> marker in output, stopping");
            break;
        }

        // Feed token back for next prediction
        llama_batch b = llama_batch_init(1, 0, 1);
        b.n_tokens = 1;
        b.token[0] = tok;
        b.pos[0] = pos++;
        b.n_seq_id[0] = 1;
        b.seq_id[0][0] = 0;
        b.logits[0] = 1;

        if (llama_decode(g_ctx, b) != 0) {
            llama_batch_free(b);
            LOGE("Decode failed at token %d", i);
            break;
        }
        llama_batch_free(b);
    }

    LOGI("Generated %d tokens, output length: %zu", generated, output.length());

    // Trim whitespace
    size_t start = output.find_first_not_of(" \n\r\t");
    size_t end = output.find_last_not_of(" \n\r\t");
    if (start != std::string::npos && end != std::string::npos) {
        output = output.substr(start, end - start + 1);
    }

    // Only save to history if generation was successful
    if (!output.empty() && generated > 0) {
        g_chat_history.push_back({user_input, output});
        
        // Keep only last 3 turns to avoid context overflow
        if (g_chat_history.size() > 3) {
            g_chat_history.erase(g_chat_history.begin());
        }
    }

    return env->NewStringUTF(output.c_str());
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

    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }

    LOGI("Shutdown complete");
}