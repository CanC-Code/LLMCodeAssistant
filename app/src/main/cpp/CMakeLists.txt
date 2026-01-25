cmake_minimum_required(VERSION 3.24.1)
project(llama_jni LANGUAGES C CXX ASM)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

# Options for llama.cpp - Must be set BEFORE add_subdirectory
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_USE_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# Create the JNI library
add_library(llama_jni SHARED llama_jni.cpp)

# Include directories for the JNI target
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/ggml/include
)

# Link against the llama and ggml targets produced by the subdirectory
find_library(log-lib log)
target_link_libraries(llama_jni 
    llama 
    ggml 
    ${log-lib}
)
