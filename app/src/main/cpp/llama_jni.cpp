#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <time.h>
#include <unistd.h>
#include <limits.h>

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

void cleanup_internal() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv * env, jobject, jint fd, jlong fileSize, jint nCtx) {
    std::lock_guard<std::mutex> lock(g_mutex);
    cleanup_internal();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    // Fix: Access file via /proc/self/fd/
    char path[PATH_MAX];
    sprintf(path, "/proc/self/fd/%d", fd);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 99; // Offload to Vulkan
    mparams.use_mmap = false; 

    g_model = llama_model_load_from_file(path, mparams);
    if (!g_model) return JNI_FALSE;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = 512;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) return JNI_FALSE;

    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist((uint32_t)time(NULL)));

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_completionNative(JNIEnv * env, jobject thiz, jstring prompt) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx || !g_model) return;

    const char* c_prompt = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab * vocab = llama_model_get_vocab(g_model); [span_7](start_span)// Fix: Get vocab[span_7](end_span)
    
    std::vector<llama_token> tokens(llama_n_ctx(g_ctx));
    int n_tokens = llama_tokenize(vocab, c_prompt, (int)strlen(c_prompt), tokens.data(), (int)tokens.size(), true, true);
    env->ReleaseStringUTFChars(prompt, c_prompt);

    llama_batch batch = llama_batch_init(512, 0, 1);
    
    for (int i = 0; i < n_tokens; i++) {
        batch.token[batch.n_tokens] = tokens[i];
        batch.pos[batch.n_tokens] = i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = (i == n_tokens - 1);
        batch.n_tokens++;
    }

    int n_cur = n_tokens;
    while (n_cur < (int)llama_n_ctx(g_ctx)) {
        if (llama_decode(g_ctx, batch)) break;

        const llama_token id = llama_sampler_sample(g_sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break; [span_8](start_span)// Fix: Use vocab_is_eog[span_8](end_span)

        char piece[128];
        int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true); [span_9](start_span)// Fix: Pass vocab[span_9](end_span)
        if (n > 0) {
            jstring jpiece = env->NewStringUTF(std::string(piece, n).c_str());
            jclass clazz = env->GetObjectClass(thiz);
            jmethodID method = env->GetMethodID(clazz, "onTokenReceived", "(Ljava/lang/String;)V");
            env->CallVoidMethod(thiz, method, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        batch.n_tokens = 0; [span_10](start_span)// Fix: Manual batch reset[span_10](end_span)
        batch.token[batch.n_tokens] = id;
        batch.pos[batch.n_tokens] = n_cur;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = true;
        batch.n_tokens++;
        n_cur++;
    }
    llama_batch_free(batch);
}
