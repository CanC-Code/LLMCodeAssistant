package io.canccode.aca

object LlamaJNI {

    init {
        // Load your native library
        System.loadLibrary("llama_jni")
    }

    /**
     * Load the model from file path.
     * @param modelPath full path to the .bin model
     * @param nCtx context size (number of tokens)
     * @param nThreads number of threads to use
     * @return true if model loaded successfully
     */
    external fun loadModel(modelPath: String, nCtx: Int, nThreads: Int): Boolean

    /**
     * Generate text from prompt using the loaded model.
     * @param prompt text prompt
     * @return generated text
     */
    external fun generateText(prompt: String): String

    /**
     * Free the model from memory
     */
    external fun freeModel()
}