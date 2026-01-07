# File: app/src/main/cpp/CMakeLists.txt
# Author: CCVO
# Purpose: CMake build for llama_jni JNI + llama.cpp static library
# Copyright: CanC-code -CCVO

cmake_minimum_required(VERSION 3.18)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Force Ninja generator on CI for faster builds
if(NOT CMAKE_GENERATOR)
    set(CMAKE_GENERATOR "Ninja" CACHE INTERNAL "" FORCE)
endif()

set(CMAKE_VERBOSE_MAKEFILE ON)

# Output directories
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../build)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../build)

# Include directories
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

target_include_directories(llama PUBLIC ../../external/llama.cpp)
target_compile_definitions(llama PRIVATE GGML_USE_CPU GGML_USE_K_QUANTS)
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