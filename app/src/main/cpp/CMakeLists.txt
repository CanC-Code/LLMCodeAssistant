cmake_minimum_required(VERSION 3.18)
project(LLMCodeAssistant LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --------------------------------------------------
# llama.cpp location
# --------------------------------------------------
set(LLAMA_DIR
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp
)

if (NOT EXISTS ${LLAMA_DIR}/llama.h)
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_DIR}")
endif()

# --------------------------------------------------
# Collect llama.cpp sources (recursive, C + C++)
# --------------------------------------------------
file(GLOB_RECURSE LLAMA_SOURCES
    ${LLAMA_DIR}/*.c
    ${LLAMA_DIR}/*.cpp
)

if (LLAMA_SOURCES STREQUAL "")
    message(FATAL_ERROR "No llama.cpp sources found in ${LLAMA_DIR}")
endif()

# --------------------------------------------------
# Build llama.cpp as static library
# --------------------------------------------------
add_library(llama STATIC ${LLAMA_SOURCES})

target_include_directories(
    llama
    PUBLIC
    ${LLAMA_DIR}
)

target_compile_definitions(
    llama
    PRIVATE
    GGML_USE_CPU
)

target_compile_options(
    llama
    PRIVATE
    -O3
    -fexceptions
    -frtti
)

# --------------------------------------------------
# JNI shared library
# --------------------------------------------------
add_library(
    llm
    SHARED
    llama_jni.cpp
)

target_include_directories(
    llm
    PRIVATE
    ${LLAMA_DIR}
)

find_library(log-lib log)

target_link_libraries(
    llm
    PRIVATE
    llama
    ${log-lib}
)