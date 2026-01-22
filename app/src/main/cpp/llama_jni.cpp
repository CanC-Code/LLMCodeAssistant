    const char * c_prompt = env->GetStringUTFChars(prompt, nullptr);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    LOGI("Tokenizing prompt (length %zu): %s", strlen(c_prompt), c_prompt);

    std::vector<llama_token> tokens(strlen(c_prompt) + 32);
    int n = llama_tokenize(
        vocab,
        c_prompt,
        strlen(c_prompt),
        tokens.data(),
        tokens.size(),
        true,   // add_special
        false   // parse_special
    );

    env->ReleaseStringUTFChars(prompt, c_prompt);

    if (n < 0) {
        LOGE("Tokenization FAILED with code %d", n);
        return env->NewStringUTF("[Unable to parse prompt - tokenizer error]");
    }

    tokens.resize(n);
    LOGI("Tokenized successfully to %d tokens", n);