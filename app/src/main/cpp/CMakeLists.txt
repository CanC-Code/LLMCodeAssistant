cmake_minimum_required(VERSION 3.18)
project(LLMCodeAssistant)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions -frtti")

# ---------------------------------------
# Core llama library (external folder)
# ---------------------------------------
add_library(llama STATIC
    ${CMAKE_SOURCE_DIR}/../../external/llama.cpp
)

target_include_directories(llama PUBLIC
    ${CMAKE_SOURCE_DIR}/../../external
)

# ---------------------------------------
# JNI shared library
# ---------------------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/../../external
)

target_link_libraries(llama_jni
    PRIVATE
        llama        # link core llama
        android      # log support
        log
)