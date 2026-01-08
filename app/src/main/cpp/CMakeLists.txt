// File: app/src/main/cpp/CMakeLists.txt
// Author: CCVO
// Purpose: Build llama native library and JNI wrapper for Android
// Copyright: CanC-code -CCVO

cmake_minimum_required(VERSION 3.18)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -----------------------------
# Paths
# -----------------------------
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../external/llama.cpp)

include_directories(${CMAKE_CURRENT_SOURCE_DIR} ${LLAMA_DIR})

# -----------------------------
# Build llama as shared library (.so)
# -----------------------------
file(GLOB LLAMA_SOURCES
    ${LLAMA_DIR}/*.cpp
    ${LLAMA_DIR}/*.c
)

add_library(
    llama
    SHARED
    ${LLAMA_SOURCES}
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

# Debug flags
set_target_properties(llama PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# -----------------------------
# JNI wrapper
# -----------------------------
add_library(
    llama_jni
    SHARED
    llama_jni.cpp
)

find_library(log-lib log)

target_link_libraries(
    llama_jni
    PRIVATE
    llama
    ${log-lib}
    android
)