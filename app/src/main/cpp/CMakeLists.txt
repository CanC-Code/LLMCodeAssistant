# File: app/src/main/cpp/CMakeLists.txt

cmake_minimum_required(VERSION 3.18)
project(LLMCodeAssistant LANGUAGES C CXX)

# ----------------------------
# C++ settings
# ----------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ----------------------------
# Paths
# ----------------------------
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

# ----------------------------
# Build llama.cpp (STATIC)
# ----------------------------
file(GLOB LLAMA_SOURCES
    ${LLAMA_DIR}/*.cpp
)

add_library(
    llama
    STATIC
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
)

target_compile_options(
    llama
    PRIVATE
    -O3
    -fexceptions
    -frtti
)

# ----------------------------
# Build JNI library (SHARED)
# ----------------------------
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