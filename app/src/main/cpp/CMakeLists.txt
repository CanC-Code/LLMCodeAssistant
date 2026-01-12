cmake_minimum_required(VERSION 3.10.2)

project(llama_jni LANGUAGES C CXX)

# -------------------------------
# Toolchain / language settings
# -------------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# -------------------------------
# Paths
# -------------------------------
# This CMakeLists.txt lives in:
# app/src/main/cpp
#
# Repo layout:
# external/llama.cpp  (git submodule)

set(LLAMA_DIR
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../external/llama.cpp
)

set(LLAMA_INCLUDE_DIR
    ${LLAMA_DIR}/include
)

set(LLAMA_SRC_DIR
    ${LLAMA_DIR}
)

# -------------------------------
# Sanity logging (non-fatal)
# -------------------------------
message(STATUS "llama.cpp root: ${LLAMA_DIR}")
message(STATUS "llama.cpp include: ${LLAMA_INCLUDE_DIR}")

# -------------------------------
# Build llama.cpp as a static lib
# -------------------------------
# llama.cpp is designed to be embedded this way

add_library(llama STATIC
    ${LLAMA_SRC_DIR}/llama.cpp
    ${LLAMA_SRC_DIR}/ggml.c
)

target_include_directories(llama PUBLIC
    ${LLAMA_INCLUDE_DIR}
    ${LLAMA_SRC_DIR}
)

target_compile_definitions(llama PRIVATE
    GGML_USE_K_QUANTS
)

# Android-specific flags (safe defaults)
target_compile_options(llama PRIVATE
    -O3
    -fvisibility=hidden
)

# -------------------------------
# JNI shared library
# -------------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_INCLUDE_DIR}
)

# Android system libs
find_library(log-lib log)

target_link_libraries(llama_jni
    llama
    ${log-lib}
)

# -------------------------------
# Gradle / Android output
# -------------------------------
# Let Gradle manage ABI split output;
# do NOT hardcode jniLibs paths

set_target_properties(llama_jni PROPERTIES
    OUTPUT_NAME "llama_jni"
)