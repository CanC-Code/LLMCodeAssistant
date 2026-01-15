# File: app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.18.1)

project(llama_jni LANGUAGES C CXX)

# ----------------------------------
# Compiler settings
# ----------------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ----------------------------------
# Paths
# ----------------------------------
# This CMakeLists.txt is in app/src/main/cpp
# Assume llama.cpp submodule is at repo_root/external/llama.cpp
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

if(NOT EXISTS ${LLAMA_ROOT}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_ROOT}")
endif()

# ----------------------------------
# Add llama.cpp submodule
# ----------------------------------
add_subdirectory(
    ${LLAMA_ROOT}
    ${CMAKE_BINARY_DIR}/llama
)

# ----------------------------------
# JNI shared library
# ----------------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Include llama headers
target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}
)

# Android log library
find_library(log-lib log)

# Link libraries
target_link_libraries(llama_jni
    llama            # llama.cpp static library
    ${log-lib}       # Android log
)

# Optional: enable warnings and optimization
target_compile_options(llama_jni PRIVATE
    -Wall -Wextra -Wpedantic
    $<$<CONFIG:Release>:-O3>
)

# Set output name explicitly
set_target_properties(llama_jni PROPERTIES OUTPUT_NAME "llama_jni")