cmake_minimum_required(VERSION 3.18)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --------------------------------------------------
# Paths
# --------------------------------------------------
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../external/llama.cpp)

if (NOT EXISTS ${LLAMA_DIR})
    message(FATAL_ERROR "llama.cpp directory not found at: ${LLAMA_DIR}")
endif()

# --------------------------------------------------
# llama.cpp sources (EXPLICIT, CI-safe)
# --------------------------------------------------
set(LLAMA_SOURCES
    ${LLAMA_DIR}/ggml.c
    ${LLAMA_DIR}/ggml-alloc.c
    ${LLAMA_DIR}/ggml-backend.c
    ${LLAMA_DIR}/ggml-quants.c
    ${LLAMA_DIR}/llama.cpp
)

foreach(src ${LLAMA_SOURCES})
    if (NOT EXISTS ${src})
        message(FATAL_ERROR "Missing llama source file: ${src}")
    endif()
endforeach()

# --------------------------------------------------
# Build llama shared library
# --------------------------------------------------
add_library(
    llama
    SHARED
    ${LLAMA_SOURCES}
)

target_include_directories(
    llama
    PUBLIC
        ${LLAMA_DIR}
)

target_compile_definitions(
    llama
    PRIVATE
        GGML_USE_CPU
        GGML_USE_K_QUANTS
)

# --------------------------------------------------
# JNI wrapper
# --------------------------------------------------
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