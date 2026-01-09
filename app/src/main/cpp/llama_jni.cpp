#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "../../external/llama.cpp/include/llama.h" // Ensure path is correct relative to cpp folder

#define LOG_TAG "LLM_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Example JNI function
extern "C"
JNIEXPORT jstring JNICALL
Java_io_canccode_aca_LLMFragment_nativeGetVersion(JNIEnv* env, jobject /* this */) {
    std::string version = "llama.cpp version: " + std::string(Llama::version());
    LOGI("Returning version: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}

// You can add more JNI wrappers here as needed