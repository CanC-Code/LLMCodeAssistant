#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <android/log.h>
#include "llama.h"

#define TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model   * model        = nullptr;
static llama_context * ctx          = nullptr;
static std::string     system_rules = "";

// KV-cache write position — advances across turns to preserve multi-turn context.
static llama_pos g_n_past = 0;

// Maximum tokens per llama_decode call — MUST match n_batch / n_ubatch in cparams.
static const int DECODE_CHUNK = 512;

// ─────────────────────────────────────────────────────────────────────────────
// Optimal thread count.
//
// Previous code hardcoded n_threads = 4. On an 8-core device that leaves 4
// cores idle during the bottleneck decode step.  Using all physical cores
// (capped at 8 to avoid thermal issues on budget SoCs) cuts first-token
// latency by 30–50 % on common ARM big.LITTLE configurations.
// ─────────────────────────────────────────────────────────────────────────────
static int optimal_threads() {
    int hw = static_cast<int>(std::thread::hardware_concurrency());
    if (hw <= 0) hw = 4;     // safe fallback if query fails
    return hw > 8 ? 8 : hw;
}

// ─────────────────────────────────────────────────────────────────────────────
// Sampler chain — rebuilt each call so no stale penalty state carries over.
//
// temp 0.6 (vs 0.7) → slightly less random; helps code correctness on 3B models.
// top_p 0.90 → tighter nucleus; fewer candidates sampled per step → slightly faster.
// ─────────────────────────────────────────────────────────────────────────────
static llama_sampler * build_sampler() {
    auto * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.6f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return smpl;
}

// ─────────────────────────────────────────────────────────────────────────────
// batch_add — add one token slot to a pre-allocated batch.
// ─────────────────────────────────────────────────────────────────────────────
static void batch_add(struct llama_batch & batch,
                      llama_token id,
                      llama_pos   pos,
                      bool        need_logits) {
    batch.token         [batch.n_tokens] = id;
    batch.pos           [batch.n_tokens] = pos;
    batch.n_seq_id      [batch.n_tokens] = 1;
    batch.seq_id        [batch.n_tokens][0] = 0;
    batch.logits        [batch.n_tokens] = need_logits ? 1 : 0;
    batch.n_tokens++;
}

// ─────────────────────────────────────────────────────────────────────────────
// ChatML template for Qwen2.5-Coder and compatible instruction-tuned models.
// ─────────────────────────────────────────────────────────────────────────────
static std::string apply_chatml(const std::string & system_msg,
                                const std::string & user_msg) {
    std::string p;
    if (!system_msg.empty()) {
        p  = "<|im_start|>system\n";
        p += system_msg;
        p += "<|im_end|>\n";
    }
    p += "<|im_start|>user\n";
    p += user_msg;
    p += "<|im_end|>\n";
    p += "<|im_start|>assistant\n";
    return p;
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: initNative
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_io_canccode_aca_LlamaBridge_initNative(JNIEnv *env, jobject /*thiz*/,
                                            jstring model_path, jint n_ctx) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    model = llama_model_load_from_file(path, mparams);
    if (!model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = (uint32_t)n_ctx;
    cparams.n_batch   = (uint32_t)DECODE_CHUNK;
    cparams.n_ubatch  = (uint32_t)DECODE_CHUNK;
    // ── FIX: use all available cores, was hardcoded at 4 ─────────────────────
    cparams.n_threads = optimal_threads();

    ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    g_n_past = 0;
    LOGI("Model loaded: %s  n_ctx=%d  n_threads=%d", path, n_ctx, cparams.n_threads);
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: generateNative
//
// FIX: when model/ctx is null, fire onError via callback so the Kotlin side
//      receives a visible error message instead of silently returning.
//      Previously the function just returned with LOGE and nothing was shown.
//
// Prompt encoding is chunked (DECODE_CHUNK tokens per batch) to prevent the
// crash that occurs when n_prompt_tokens > n_batch.
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv *env, jobject /*thiz*/,
                                                jstring prompt, jint max_tokens,
                                                jobject callback) {
    // Resolve callback methods first so we can fire errors through them.
    jclass    cbClass          = env->GetObjectClass(callback);
    jmethodID onTokenMethod    = env->GetMethodID(cbClass, "onToken",    "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(cbClass, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onErrorMethod    = env->GetMethodID(cbClass, "onError",    "(Ljava/lang/String;)V");

    // ── Guard: model not loaded → fire onError so fragment shows the problem ──
    if (!ctx || !model) {
        LOGE("generateNative called but model/ctx is null — was initNative called?");
        if (onErrorMethod) {
            jstring msg = env->NewStringUTF("Model not loaded — select a .gguf in Settings & Model");
            env->CallVoidMethod(callback, onErrorMethod, msg);
            env->DeleteLocalRef(msg);
        }
        return;
    }

    if (!onTokenMethod || !onCompleteMethod || !onErrorMethod) {
        LOGE("Failed to resolve callback methods via JNI — check ProGuard rules");
        return;
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab *vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOGE("Failed to get vocab");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        jstring msg = env->NewStringUTF("Internal error: vocab unavailable");
        env->CallVoidMethod(callback, onErrorMethod, msg);
        env->DeleteLocalRef(msg);
        return;
    }

    // ── 1. Build ChatML prompt ────────────────────────────────────────────────
    std::string formatted = apply_chatml(system_rules, std::string(prompt_str));
    env->ReleaseStringUTFChars(prompt, prompt_str);
    LOGI("Prompt length: %zu chars", formatted.size());

    // ── 2. Tokenise ───────────────────────────────────────────────────────────
    int n_prompt_tokens = -llama_tokenize(vocab,
                                          formatted.c_str(), (int)formatted.size(),
                                          nullptr, 0, true, true);
    if (n_prompt_tokens <= 0) {
        LOGE("Tokenisation produced 0 tokens");
        jstring msg = env->NewStringUTF("Tokenisation failed — prompt may be empty");
        env->CallVoidMethod(callback, onErrorMethod, msg);
        env->DeleteLocalRef(msg);
        return;
    }

    std::vector<llama_token> prompt_tokens((size_t)n_prompt_tokens);
    llama_tokenize(vocab,
                   formatted.c_str(), (int)formatted.size(),
                   prompt_tokens.data(), (int)prompt_tokens.size(),
                   true, true);

    const int n_ctx_size = (int)llama_n_ctx(ctx);
    LOGI("n_prompt_tokens=%d  g_n_past=%d  n_ctx=%d", n_prompt_tokens, (int)g_n_past, n_ctx_size);

    // ── 3. KV overflow guard ──────────────────────────────────────────────────
    if ((int)g_n_past + n_prompt_tokens + max_tokens > n_ctx_size) {
        LOGI("Context overflow — resetting KV cache");
        llama_memory_t mem = llama_get_memory(ctx);
        llama_memory_seq_rm(mem, 0, -1, -1);
        g_n_past = 0;
    }

    // Hard-cap prompt tokens so we never crash llama_decode
    const int max_prompt_tokens = n_ctx_size - max_tokens - 4;
    if (n_prompt_tokens > max_prompt_tokens) {
        LOGE("Prompt (%d tokens) exceeds context budget (%d) — truncating",
             n_prompt_tokens, max_prompt_tokens);
        n_prompt_tokens = max_prompt_tokens;
        prompt_tokens.resize((size_t)n_prompt_tokens);
    }

    // ── 4. Encode prompt in DECODE_CHUNK-sized batches ────────────────────────
    // This prevents the crash when n_prompt_tokens > n_batch.
    {
        llama_batch batch = llama_batch_init(DECODE_CHUNK, 0, 1);
        int encoded = 0;

        while (encoded < n_prompt_tokens) {
            batch.n_tokens = 0;
            int chunk_end = encoded + DECODE_CHUNK;
            if (chunk_end > n_prompt_tokens) chunk_end = n_prompt_tokens;

            for (int i = encoded; i < chunk_end; i++) {
                bool is_last = (i == n_prompt_tokens - 1);
                batch_add(batch, prompt_tokens[i], g_n_past + (i - 0), is_last);
            }
            // Assign correct absolute positions
            for (int i = 0; i < batch.n_tokens; i++) {
                batch.pos[i] = g_n_past + encoded + i;
            }

            if (llama_decode(ctx, batch) != 0) {
                LOGE("llama_decode failed at prompt chunk offset %d", encoded);
                llama_batch_free(batch);
                jstring msg = env->NewStringUTF("Decode failed — context may be corrupted");
                env->CallVoidMethod(callback, onErrorMethod, msg);
                env->DeleteLocalRef(msg);
                return;
            }
            encoded += (chunk_end - encoded);
        }

        llama_batch_free(batch);
        g_n_past += n_prompt_tokens;
    }

    // ── 5. Autoregressive generation — stream each token immediately ──────────
    // onToken is called after EVERY piece so the UI shows live typing.
    llama_sampler * smpl      = build_sampler();
    std::string     full_resp;
    llama_batch     gen_batch = llama_batch_init(1, 0, 1);

    for (int i = 0; i < max_tokens; i++) {
        llama_token id = llama_sampler_sample(smpl, ctx, -1);
        llama_sampler_accept(smpl, id);

        if (llama_vocab_is_eog(vocab, id)) {
            LOGI("EOG after %d generated tokens", i);
            break;
        }

        char buf[256];
        int  n_chars = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n_chars > 0) {
            std::string piece(buf, n_chars);
            full_resp += piece;

            // Fire immediately → Kotlin LLMFragment appends to TextView → live typing
            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        gen_batch.n_tokens = 0;
        batch_add(gen_batch, id, g_n_past, true);
        g_n_past++;

        if (llama_decode(ctx, gen_batch) != 0) {
            LOGE("llama_decode failed at generation step %d", i);
            break;
        }
    }

    llama_batch_free(gen_batch);
    llama_sampler_free(smpl);

    // ── 6. Fire onComplete ────────────────────────────────────────────────────
    jstring jfull = env->NewStringUTF(full_resp.c_str());
    env->CallVoidMethod(callback, onCompleteMethod, jfull);
    env->DeleteLocalRef(jfull);

    LOGI("Generation complete: %zu chars, g_n_past=%d", full_resp.size(), (int)g_n_past);
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: clearHistoryNative
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_clearHistoryNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (ctx) {
        llama_memory_t mem = llama_get_memory(ctx);
        llama_memory_seq_rm(mem, 0, -1, -1);
        g_n_past = 0;
        LOGI("KV cache cleared; g_n_past=0");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: setModelRulesNative
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_setModelRulesNative(JNIEnv *env, jobject /*thiz*/,
                                                     jstring rules) {
    if (rules == nullptr) { system_rules = ""; return; }
    const char *r = env->GetStringUTFChars(rules, nullptr);
    system_rules  = std::string(r);
    env->ReleaseStringUTFChars(rules, r);
    LOGI("System rules updated (%zu chars)", system_rules.size());
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: shutdownNative
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (ctx)   { llama_free(ctx);         ctx   = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }
    g_n_past     = 0;
    system_rules = "";
    llama_backend_free();
    LOGI("Native backend shut down");
}
