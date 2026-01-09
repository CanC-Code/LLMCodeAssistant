cmake_minimum_required(VERSION 3.18)
project(LLamaAndroid)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android NDK flags
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions -frtti")

# ---------------------------------------
# Core llama library
# ---------------------------------------
add_library(llama STATIC
    llama/llama.cpp
    # Add any other core files from llama here
)

target_include_directories(llama PUBLIC
    llama
)

# ---------------------------------------
# JNI shared library
# ---------------------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    llama
)

target_link_libraries(llama_jni
    PRIVATE
        llama        # link core llama
        android      # log support
        log
)