cmake_minimum_required(VERSION 3.18.1)

project(llm_native LANGUAGES C CXX)

# ---------------------------
# C++ standard
# ---------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------
# Paths
# ---------------------------
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/../../../external/llama.cpp)

# ---------------------------
# llama.cpp options (minimal, Android-safe)
# ---------------------------
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_STATIC OFF CACHE BOOL "" FORCE)

# ---------------------------
# Add llama.cpp
# ---------------------------
add_subdirectory(${LLAMA_DIR} llama)

# ---------------------------
# JNI library
# ---------------------------
add_library(
        llm_jni
        SHARED
        llama_jni.cpp
)

# ---------------------------
# Include paths
# ---------------------------
target_include_directories(
        llm_jni
        PRIVATE
        ${LLAMA_DIR}
        ${LLAMA_DIR}/include
)

# ---------------------------
# Link libraries
# ---------------------------
target_link_libraries(
        llm_jni
        llama
        android
        log
)