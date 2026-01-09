cmake_minimum_required(VERSION 3.18.1)
project(llama_jni)

# Define the shared library
add_library(
    llama_jni
    SHARED
    llama_jni.cpp
)

# Include directories for llama.cpp
target_include_directories(
    llama_jni
    PRIVATE
    ${CMAKE_SOURCE_DIR}/../../external/llama.cpp/include
    ${CMAKE_SOURCE_DIR}/../../external/llama.cpp/ggml/include
)

# Set C++ standard
set_target_properties(llama_jni PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# Enable exceptions and RTTI for Android
target_compile_options(llama_jni PRIVATE
    -fexceptions
    -frtti
)