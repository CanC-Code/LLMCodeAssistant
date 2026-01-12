cmake_minimum_required(VERSION 3.10.2)
project("llama_jni")

# Use C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- Paths ---

# Current CMakeLists.txt is in app/src/main/cpp
# Correct relative path to external/llama.cpp
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../external/llama.cpp")
set(LLAMA_INCLUDE "${LLAMA_DIR}/include")
set(GGML_INCLUDE "${LLAMA_DIR}/ggml/include")  # optional if ggml exists

# --- Check llama.h exists ---
if(EXISTS "${LLAMA_INCLUDE}/llama.h")
    message(STATUS "Building llama_jni: llama.h found at ${LLAMA_INCLUDE}")

    # Add shared JNI library
    add_library(llama_jni SHARED llama_jni.cpp)

    # Include directories for compilation
    target_include_directories(llama_jni PRIVATE
        "${LLAMA_INCLUDE}"
        "${GGML_INCLUDE}"
    )

    # Link Android log library
    find_library(log-lib log)
    target_link_libraries(llama_jni ${log-lib})

    # Output directory for Gradle
    set_target_properties(llama_jni PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/../jniLibs/${ANDROID_ABI}"
    )

else()
    message(FATAL_ERROR "Cannot find llama.h at ${LLAMA_INCLUDE}. JNI build skipped.")
endif()