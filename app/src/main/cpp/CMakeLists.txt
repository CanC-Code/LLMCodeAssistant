cmake_minimum_required(VERSION 3.24.1)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

# Critical: Added missing ggml and src paths
target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/ggml/include
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(GGML_USE_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)
add_library(llama_jni SHARED llama_jni.cpp)

find_library(log-lib log)
target_link_libraries(llama_jni llama ggml ${log-lib})
