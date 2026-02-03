#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <time.h>
#include <unistd.h> // Required for read/access

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

// Helper to clean up previous state
void cleanup_internal() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(
        JNIEnv * env,
        jobject,
        jint fd,        
        jlong fileSize, 
        jint nCtx) {

    std::lock_guard<std::mutex> lock(g_mutex);
    cleanup_internal();
    g_chat_history.clear();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    LOGI("Loading model via FD: %d (Size: %lld)", fd, (long long)fileSize);

    // FIX: Access the file via /proc/self/fd/
    // This allows the standard llama_model_load_from_file to read the open FD
    char path[PATH_MAX];
    sprintf(path, "/proc/self/fd/%d", fd);

    llama_model_params mparams = llama_model_default_params();
    
    // Enable Vulkan GPU acceleration (matches your CMake setup)
    // 99 is a common shorthand to offload all layers to GPU
    mparams.n_gpu_layers = 99; 
    
    // mmap can be problematic with some Android FDs; keeping it off for safety
    mparams.use_mmap = false; 

    // Load using the proc path
    g_model = llama_model_load_from_file(path, mparams);

    if (!g_model) {
        LOGE("Model load failed. Check if FD %d is valid and readable.", fd);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx   = nCtx;
    cparams.n_batch = 512;
    // Android performance tip: use 4-6 threads for high-perf cores
    cparams.n_threads = 6; 
    cparams.n_threads_batch = 6;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Context creation failed");
        return JNI_FALSE;
    }

    // Modern Sampler initialization logic
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sparams.no_perf = false;
    
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist((uint32_t)time(NULL)));

    LOGI("Llama model initialized successfully via Vulkan/CPU");
    return JNI_TRUE;
}
