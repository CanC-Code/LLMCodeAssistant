cmake_minimum_required(VERSION 3.18.1)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

if(NOT EXISTS ${LLAMA_ROOT}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_ROOT}")
endif()

# Configuration
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(GGML_USE_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_USE_LLAMAFILE OFF CACHE BOOL "" FORCE) 

add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

add_library(llama_jni SHARED llama_jni.cpp)

# Expanded include paths for modern submodule structure
target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/ggml/include
)

find_library(log-lib log)
target_link_libraries(llama_jni llama ggml ${log-lib})
