cmake_minimum_required(VERSION 3.22.1)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Submodule path at repo root
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

# CRITICAL: Embed shaders to fix ".spv (No such file or directory)" errors
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(GGML_VULKAN_SHADER_EMBED ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Use headers from workflow if available
if(Vulkan_INCLUDE_DIR)
    include_directories(${Vulkan_INCLUDE_DIR})
endif()

add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

add_library(llama_jni SHARED llama_jni.cpp)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/common
    ${LLAMA_ROOT}/ggml/include
)

find_library(log-lib log)
find_library(vulkan-lib vulkan)

target_link_libraries(llama_jni llama ggml ${log-lib} ${vulkan-lib})

target_compile_options(llama_jni PRIVATE -Wall -O3)
