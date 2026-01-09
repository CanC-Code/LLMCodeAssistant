cmake_minimum_required(VERSION 3.18)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to llama.cpp
set(LLAMA_CPP_DIR "${CMAKE_SOURCE_DIR}/../../external/llama.cpp")

include_directories(
    ${CMAKE_SOURCE_DIR}
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

file(GLOB LLAMA_SOURCES
    ${LLAMA_CPP_DIR}/*.cpp
    ${LLAMA_CPP_DIR}/ggml/src/*.cpp
)

add_library(
    llama_jni
    SHARED
    ${CMAKE_SOURCE_DIR}/llama_jni.cpp
    ${LLAMA_SOURCES}
)

# Android-specific flags
target_compile_definitions(llama_jni PRIVATE ANDROID)
target_compile_options(llama_jni PRIVATE -fexceptions -frtti)

find_library(log-lib log)

target_link_libraries(
    llama_jni
    ${log-lib}
)