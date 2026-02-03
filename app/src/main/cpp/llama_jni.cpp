#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <time.h>

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

// Helper to clean up previous state
void cleanup_internal() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }
}

// Tokenization helper
static std::vector<llama_token> tokenize(const struct llama_vocab * vocab, const std::string & text, bool add_special) {
    // Determine required size
    int n_tokens = llama_tokenize(vocab, text.c_str(), text.length(), NULL, 0, add_special, true);
    std::vector<llama_token> res(abs(n_tokens));
    if (llama_tokenize(vocab, text.c_str(), text.length(), res.data(), res.size(), add_special, true) < 0) {
        LOGE("Tokenization failed");
    }
    return res;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(
        JNIEnv * env,
        jobject,
        jint fd,        // Updated to accept File Descriptor
        jlong fileSize, // Updated to accept File Size
        jint nCtx) {

    std::lock_guard<std::mutex> lock(g_mutex);
    cleanup_internal();
    g_chat_history.clear();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    LOGI("Loading model from FD: %d (Size: %lld)", fd, (long long)fileSize);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // Set higher if using Vulkan/CL
    mparams.use_mmap = false; // MUST be false for FD loading

    // Load from File Descriptor (Modern llama.cpp API)
    g_model = llama_model_load_from_fd(fd, (size_t)fileSize, mparams);

    if (!g_model) {
        LOGE("Model load failed from FD");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = 512;
    cparams.n_threads = 4;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Context creation failed");
        return JNI_FALSE;
    }

    // Modern Sampler initialization
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    // Fixed: Use manual seed instead of LLAMA_DEFAULT_SEED
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist((uint32_t)time(NULL)));

    LOGI("Model ready from user-provided file");
    return JNI_TRUE;
}

// ... Keep setModelRulesNative, clearHistoryNative, and generateNative logic ...
// (Ensure generateNative uses llama_kv_cache_clear(g_ctx) if you need to reset sequence)
