#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define TAG "LLAMA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model   * model   = nullptr;
static llama_context * ctx     = nullptr;
static std::string system_rules = "";

// Tracks the current write position in the KV cache across turns so
// multi-turn conversation context is preserved correctly.
static llama_pos g_n_past = 0;

// ─────────────────────────────────────────────────────────────────────────────
// Helper: build a fresh sampler chain each generation call.
// Using a fresh chain every time avoids stale penalty state from prior turns.
// Sampler stack (bottom → top):
//   1. Repetition penalty   – penalises recently seen tokens
//   2. Temperature          – softens the distribution
//   3. Top-K               – keeps only the K most-likely tokens
//   4. Top-P (nucleus)      – keeps the smallest set covering P probability mass
//   5. Min-P               – removes tokens below min fraction of top probability
//   6. Distribution sampler – draws from the resulting distribution
// ─────────────────────────────────────────────────────────────────────────────
static llama_sampler * build_sampler() {
    auto * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());

    // Repetition penalty over the last 64 tokens; penalise repeated tokens 1.1×
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        64,    // last_n  – penalty window
        1.1f,  // repeat_penalty
        0.0f,  // frequency_penalty
        0.0f   // presence_penalty
    ));

    // Temperature – 0.7 is a good default for instruction-tuned coding models
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));

    // Top-K – limit to top 40 tokens before nucleus sampling
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));

    // Top-P – nucleus sampling at 0.95
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));

    // Min-P – remove tokens below 5% of the top token probability
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));

    // Final: draw from the filtered distribution
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return smpl;
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: add one token to a batch
// ─────────────────────────────────────────────────────────────────────────────
static void batch_add(struct llama_batch & batch,
                      llama_token id,
                      llama_pos   pos,
                      bool        need_logits) {
    batch.token   [batch.n_tokens] = id;
    batch.pos     [batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id  [batch.n_tokens][0] = 0;
    batch.logits  [batch.n_tokens] = need_logits ? 1 : 0;
    batch.n_tokens++;
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: wrap a raw user string in the ChatML template that Qwen2.5-Coder
// (and most modern instruction-tuned GGUF models) expect.
//
//   <|im_start|>system
//   {system}<|im_end|>
//   <|im_start|>user
//   {user}<|im_end|>
//   <|im_start|>assistant
//
// The trailing "<|im_start|>assistant\n" primes the model to reply.
// ─────────────────────────────────────────────────────────────────────────────
static std::string apply_chatml(const std::string & system_msg,
                                const std::string & user_msg) {
    std::string prompt;
    if (!system_msg.empty()) {
        prompt  = "<|im_start|>system\n";
        prompt += system_msg;
        prompt += "<|im_end|>\n";
    }
    prompt += "<|im_start|>user\n";
    prompt += user_msg;
    prompt += "<|im_end|>\n";
    prompt += "<|im_start|>assistant\n";
    return prompt;
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
    cparams.n_ctx      = (uint32_t)n_ctx;
    cparams.n_batch    = 512;
    cparams.n_threads  = 4;   // good default for mobile; tune per device

    ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create llama context");
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    g_n_past = 0;

    LOGI("Model loaded: %s  ctx=%d", path, n_ctx);
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: generateNative
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_generateNative(JNIEnv *env, jobject /*thiz*/,
                                                jstring prompt, jint max_tokens,
                                                jobject callback) {
    if (!ctx || !model) {
        LOGE("generateNative called but model/ctx is null");
        return;
    }

    jclass     cbClass         = env->GetObjectClass(callback);
    jmethodID  onTokenMethod   = env->GetMethodID(cbClass, "onToken",    "(Ljava/lang/String;)V");
    jmethodID  onCompleteMethod= env->GetMethodID(cbClass, "onComplete", "(Ljava/lang/String;)V");

    const char * prompt_str = env->GetStringUTFChars(prompt, nullptr);
    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOGE("Failed to get vocab");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return;
    }

    // ── 1. Build the ChatML-formatted prompt ──────────────────────────────────
    std::string formatted = apply_chatml(system_rules, std::string(prompt_str));
    env->ReleaseStringUTFChars(prompt, prompt_str);

    LOGI("Prompt (first 120 chars): %.120s", formatted.c_str());

    // ── 2. Tokenise ───────────────────────────────────────────────────────────
    // Determine the token count first (negative return = required buffer size)
    int n_prompt_tokens = -llama_tokenize(vocab,
                                          formatted.c_str(),
                                          (int)formatted.size(),
                                          nullptr, 0,
                                          /*add_special=*/true,
                                          /*parse_special=*/true);
    if (n_prompt_tokens <= 0) {
        LOGE("Tokenisation returned zero tokens");
        return;
    }

    std::vector<llama_token> prompt_tokens((size_t)n_prompt_tokens);
    llama_tokenize(vocab,
                   formatted.c_str(),
                   (int)formatted.size(),
                   prompt_tokens.data(),
                   (int)prompt_tokens.size(),
                   /*add_special=*/true,
                   /*parse_special=*/true);

    LOGI("Prompt token count: %d  KV past: %d", n_prompt_tokens, (int)g_n_past);

    // ── 3. Guard: reset KV cache if we would overflow the context window ──────
    const int n_ctx_size = (int)llama_n_ctx(ctx);
    if ((int)g_n_past + n_prompt_tokens + max_tokens > n_ctx_size) {
        LOGI("Context window would overflow — resetting KV cache");
        llama_memory_t mem = llama_get_memory(ctx);
        llama_memory_seq_rm(mem, 0, -1, -1);
        g_n_past = 0;
    }

    // ── 4. Encode prompt tokens into the KV cache ─────────────────────────────
    {
        llama_batch batch = llama_batch_init(n_prompt_tokens, 0, 1);
        for (int i = 0; i < n_prompt_tokens; i++) {
            // Only the last token needs logits for sampling
            batch_add(batch, prompt_tokens[i], g_n_past + i,
                      /*need_logits=*/(i == n_prompt_tokens - 1));
        }

        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode failed for prompt batch");
            llama_batch_free(batch);
            return;
        }
        llama_batch_free(batch);
        g_n_past += n_prompt_tokens;
    }

    // ── 5. Autoregressive decode loop ─────────────────────────────────────────
    // Build a fresh sampler so there is no stale penalty state from prior turns.
    llama_sampler * smpl = build_sampler();

    std::string full_response;
    llama_batch  gen_batch = llama_batch_init(1, 0, 1);

    for (int i = 0; i < max_tokens; i++) {
        // Sample the next token
        llama_token id = llama_sampler_sample(smpl, ctx, -1);

        // Inform the sampler of the chosen token (updates penalty history)
        llama_sampler_accept(smpl, id);

        // Stop on end-of-generation token
        if (llama_vocab_is_eog(vocab, id)) {
            LOGI("EOG token reached after %d generated tokens", i);
            break;
        }

        // Decode the token id to a UTF-8 piece
        char buf[256];
        int  n_chars = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n_chars > 0) {
            std::string piece(buf, n_chars);
            full_response += piece;

            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        // Feed the chosen token back so the next sample conditions on it
        gen_batch.n_tokens = 0;
        batch_add(gen_batch, id, g_n_past, /*need_logits=*/true);
        g_n_past++;

        if (llama_decode(ctx, gen_batch) != 0) {
            LOGE("llama_decode failed at generation step %d", i);
            break;
        }
    }

    llama_batch_free(gen_batch);
    llama_sampler_free(smpl);

    // ── 6. Deliver the complete response ──────────────────────────────────────
    jstring jfull = env->NewStringUTF(full_response.c_str());
    env->CallVoidMethod(callback, onCompleteMethod, jfull);
    env->DeleteLocalRef(jfull);

    LOGI("Generation complete. Total response length: %zu chars", full_response.size());
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: clearHistoryNative — wipes the KV cache and resets the position counter
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_clearHistoryNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (ctx) {
        llama_memory_t mem = llama_get_memory(ctx);
        llama_memory_seq_rm(mem, 0, -1, -1);
        g_n_past = 0;
        LOGI("KV cache cleared; g_n_past reset to 0");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: setModelRulesNative
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_setModelRulesNative(JNIEnv *env, jobject /*thiz*/,
                                                     jstring rules) {
    if (rules == nullptr) {
        system_rules = "";
        return;
    }
    const char *rules_str = env->GetStringUTFChars(rules, nullptr);
    system_rules = std::string(rules_str);
    env->ReleaseStringUTFChars(rules, rules_str);
    LOGI("System rules updated (%zu chars)", system_rules.size());
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI: shutdownNative
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_io_canccode_aca_LlamaBridge_shutdownNative(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (ctx)   { llama_free(ctx);         ctx   = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }
    g_n_past = 0;
    llama_backend_free();
    LOGI("Native backend shut down");
}
