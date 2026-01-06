cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -----------------------------
# Paths
# -----------------------------
# Path to the llama.cpp submodule
set(LLAMA_PATH ${CMAKE_CURRENT_SOURCE_DIR}/../../external/llama.cpp)

# -----------------------------
# llama.cpp static library
# -----------------------------
add_library(
    llama
    STATIC
    ${LLAMA_PATH}/llama.cpp
    ${LLAMA_PATH}/ggml.c
    ${LLAMA_PATH}/ggml-alloc.c
    ${LLAMA_PATH}/ggml-backend.c
    ${LLAMA_PATH}/ggml-cpu.c
    ${LLAMA_PATH}/ggml-quants.c
)

target_include_directories(
    llama
    PUBLIC
    ${LLAMA_PATH}
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