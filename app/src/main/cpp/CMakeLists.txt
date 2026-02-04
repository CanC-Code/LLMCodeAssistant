cmake_minimum_required(VERSION 3.22.1)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 1. Path to llama.cpp submodule
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

# 2. Critical Vulkan Configuration (Must be set BEFORE add_subdirectory)
set(GGML_VULKAN ON CACHE BOOL "Use Vulkan" FORCE)

# FIX: Embed shaders to prevent "No such file or directory" errors for .spv files
set(GGML_VULKAN_SHADER_EMBED ON CACHE BOOL "Embed Vulkan shaders" FORCE)

set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# 3. Vulkan Header Resolution
# Ensures the compiler can find the headers installed in your GitHub runner
if(Vulkan_INCLUDE_DIR)
    include_directories(${Vulkan_INCLUDE_DIR})
endif()

# Add the llama.cpp project
add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# 4. JNI shared library
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/common
    ${LLAMA_ROOT}/ggml/include
    ${LLAMA_ROOT}/ggml/src
)

find_library(log-lib log)
find_library(vulkan-lib vulkan)

# Link both 'llama' and 'ggml' targets
target_link_libraries(llama_jni
    llama
    ggml
    ${log-lib}
    ${vulkan-lib}
)

target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
    -fexceptions
    $<$<CONFIG:Release>:-O3 -march=armv8-a+dotprod+crypto>
)
