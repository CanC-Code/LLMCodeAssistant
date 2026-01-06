# File: app/src/main/cpp/CMakeLists.txt
# Author: CCVO
# Purpose: Build llama.cpp as a shared library for Android

cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)

add_library(
        llama_jni
        SHARED
        llama_jni.cpp
        llama/llama.cpp
        llama/ggml.c
        llama/ggml-alloc.c
        llama/ggml-backend.c
        llama/ggml-quants.c
)

target_include_directories(
        llama_jni
        PRIVATE
        llama
        llama/include
)

find_library(log-lib log)

target_link_libraries(
        llama_jni
        ${log-lib}
)