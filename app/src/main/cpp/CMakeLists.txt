cmake_minimum_required(VERSION 3.10.2)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ----------------------------------
# Paths
# ----------------------------------
# CMakeLists.txt is in app/src/main/cpp
# llama.cpp submodule is at external/llama.cpp

set(LLAMA_ROOT
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../external/llama.cpp
)

# ----------------------------------
# Add llama.cpp (upstream CMake)
# ----------------------------------
# This uses llama.cpp's own CMakeLists.txt
# and avoids guessing source files.

add_subdirectory(
    ${LLAMA_ROOT}
    ${CMAKE_BINARY_DIR}/llama
)

# ----------------------------------
# JNI library
# ----------------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
)

# Android log lib
find_library(log-lib log)

target_link_libraries(llama_jni
    llama
    ${log-lib}
)