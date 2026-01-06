cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -----------------------------
# llama.cpp static library
# -----------------------------
add_library(
    llama
    STATIC
    llama/llama.cpp
    llama/ggml.c
    llama/ggml-alloc.c
    llama/ggml-backend.c
    llama/ggml-cpu.c
    llama/ggml-quants.c
)

target_include_directories(
    llama
    PUBLIC
    llama
)

target_compile_definitions(
    llama
    PRIVATE
    GGML_USE_CPU
    GGML_USE_K_QUANTS
)

# Optimization flags
target_compile_options(llama PRIVATE -O3)

# -----------------------------
# JNI shared library
# -----------------------------
add_library(
    llama_jni
    SHARED
    llama_jni.cpp
)

# Android log library
find_library(log-lib log)

target_link_libraries(
    llama_jni
    PRIVATE
    llama
    ${log-lib}
    android
)