# File: app/src/main/cpp/CMakeLists.txt
# Purpose: Build llama_jni.cpp and llama.cpp as a shared library for Android

cmake_minimum_required(VERSION 3.22)
project("llmassistant")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Define the shared library
add_library(
    llm                 # Library name
    SHARED              # Build as shared library
    llama_jni.cpp       # JNI wrapper
    ../../external/llama.cpp  # LLM core
)

# Include directories
target_include_directories(
    llm
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}       # llama_jni.cpp
        ../../external                     # llama.h
)

# Link against Android log library for __android_log_print
find_library(
    log-lib
    log
)

target_link_libraries(
    llm
    ${log-lib}
)