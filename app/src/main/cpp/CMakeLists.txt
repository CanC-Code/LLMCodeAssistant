cmake_minimum_required(VERSION 3.18.1)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ----------------------------
# llama.cpp path
# ----------------------------
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

if(NOT EXISTS ${LLAMA_ROOT}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp submodule not found at ${LLAMA_ROOT}. Please run 'git submodule update --init --recursive'")
endif()

# ----------------------------
# llama.cpp configuration
# ----------------------------
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Optimization settings for Android ARM
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
# Most Android devices don't benefit from OpenMP due to thermal throttling
set(GGML_USE_OPENMP OFF CACHE BOOL "" FORCE) 

# CRITICAL: Disable llamafile to avoid FP16 issues on ARM hardware
set(GGML_USE_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_USE_CPU_REPACK OFF CACHE BOOL "" FORCE)

# Enable K-Quants for GGUF support (required for Qwen2.5-Coder-3B)
set(LLAMA_K_QUANTS ON CACHE BOOL "" FORCE)

# Add the llama.cpp directory
add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# ----------------------------
# JNI shared library
# ----------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}
    ${LLAMA_ROOT}/include
)

# Android log library
find_library(log-lib log)

target_link_libraries(llama_jni
    llama
    ${log-lib}
)

# Advanced Compiler Flags for Mobile
target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -fexceptions
    $<$<CONFIG:Release>:
        -O3 
        -flto 
        -march=armv8-a+dotprod+crypto
        -mcpu=generic
    >
)

set_target_properties(llama_jni PROPERTIES
    OUTPUT_NAME "llama_jni"
)
