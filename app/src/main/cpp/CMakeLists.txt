cmake_minimum_required(VERSION 3.18)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------
# Paths
# ---------------------------------------
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../external/llama.cpp)

# ---------------------------------------
# llama.cpp core sources ONLY
# (No examples, no CLI, no tools)
# ---------------------------------------
set(LLAMA_SOURCES
    ${LLAMA_DIR}/ggml.c
    ${LLAMA_DIR}/ggml-alloc.c
    ${LLAMA_DIR}/ggml-backend.c
    ${LLAMA_DIR}/ggml-quants.c
    ${LLAMA_DIR}/ggml-cpu.c
    ${LLAMA_DIR}/llama.cpp
)

add_library(llama SHARED ${LLAMA_SOURCES})

target_include_directories(llama
    PUBLIC
        ${LLAMA_DIR}
)

# ---------------------------------------
# Android-safe compile flags
# ---------------------------------------
target_compile_definitions(llama
    PRIVATE
        GGML_USE_CPU
        GGML_USE_K_QUANTS
        GGML_DISABLE_OPENMP
        GGML_DISABLE_METAL
        GGML_DISABLE_CUDA
        GGML_DISABLE_ACCELERATE
        GGML_DISABLE_CLBLAST
)

target_compile_options(llama
    PRIVATE
        -fno-exceptions
        -fno-rtti
)

# ---------------------------------------
# JNI wrapper
# ---------------------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

find_library(log-lib log)

target_link_libraries(llama_jni
    PRIVATE
        llama
        ${log-lib}
        android
)