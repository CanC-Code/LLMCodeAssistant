# File: app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.18)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -----------------------------
# Include paths
# -----------------------------
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../../external/llama.cpp
)

# -----------------------------
# llama static library
# -----------------------------
add_library(
    llama
    STATIC
    ../../external/llama.cpp/llama.cpp
    ../../external/llama.cpp/ggml.c
    ../../external/llama.cpp/ggml-alloc.c
    ../../external/llama.cpp/ggml-backend.c
    ../../external/llama.cpp/ggml-cpu.c
    ../../external/llama.cpp/ggml-quants.c
)

target_include_directories(
    llama
    PUBLIC
    ../../external/llama.cpp
)

target_compile_definitions(
    llama
    PRIVATE
    GGML_USE_CPU
    GGML_USE_K_QUANTS
)

# Heavy optimization only in Release mode
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(llama PRIVATE -O3)
endif()

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