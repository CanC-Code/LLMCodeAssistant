cmake_minimum_required(VERSION 3.18)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -----------------------------
# Include paths
# -----------------------------
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../external/llama.cpp)
include_directories(${CMAKE_CURRENT_SOURCE_DIR} ${LLAMA_DIR})

# -----------------------------
# llama static library
# -----------------------------
add_library(
    llama
    STATIC
    ${LLAMA_DIR}/llama.cpp
    ${LLAMA_DIR}/ggml.c
    ${LLAMA_DIR}/ggml-alloc.c
    ${LLAMA_DIR}/ggml-backend.c
    ${LLAMA_DIR}/ggml-cpu.c
    ${LLAMA_DIR}/ggml-quants.c
)

target_include_directories(
    llama
    PUBLIC ${LLAMA_DIR}
)

target_compile_definitions(
    llama
    PRIVATE
    GGML_USE_CPU
    GGML_USE_K_QUANTS
)

# Optimization handled by Gradle build type
# Gradle passes -O0 for Debug and -O3 for Release automatically

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