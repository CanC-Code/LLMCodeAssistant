cmake_minimum_required(VERSION 3.10.2)
project("llama_jni")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to external llama.cpp
set(LLAMA_DIR "${CMAKE_SOURCE_DIR}/../../external/llama.cpp")
set(LLAMA_INCLUDE "${LLAMA_DIR}/include")
set(GGML_INCLUDE "${LLAMA_DIR}/ggml/include")  # optional if ggml exists

# Only build JNI library if llama.h exists
if(EXISTS "${LLAMA_INCLUDE}/llama.h")
    message(STATUS "Building llama_jni: llama.h found")

    add_library(llama_jni SHARED llama_jni.cpp)

    target_include_directories(llama_jni PRIVATE
        "${LLAMA_INCLUDE}"
        "${GGML_INCLUDE}"
    )

    find_library(log-lib log)
    target_link_libraries(llama_jni ${log-lib})

else()
    message(WARNING "Skipping llama_jni build: ${LLAMA_INCLUDE}/llama.h not found. The JNI library will not be built.")
endif()