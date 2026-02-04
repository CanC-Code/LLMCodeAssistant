cmake_minimum_required(VERSION 3.22.1)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 1. Path to llama.cpp submodule
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

# 2. Critical Vulkan Configuration (Must be set BEFORE add_subdirectory)
# We use CACHE PATH/BOOL to ensure these persist into the llama.cpp sub-project
set(GGML_VULKAN ON CACHE BOOL "Use Vulkan" FORCE)
set(GGML_VULKAN_SHADER_EMBED ON CACHE BOOL "Embed Vulkan shaders" FORCE)

set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# 3. Vulkan Header Resolution
# Fix: Pass the include dir to the sub-project so ggml-vulkan.cpp can find vulkan.hpp
if(Vulkan_INCLUDE_DIR)
    message(STATUS "Vulkan headers found at: ${Vulkan_INCLUDE_DIR}")
    include_directories(${Vulkan_INCLUDE_DIR})
    # This specifically tells the Vulkan find module where to look
    set(Vulkan_INCLUDE_DIRS ${Vulkan_INCLUDE_DIR} CACHE PATH "" FORCE)
endif()

# Add the llama.cpp project
add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# 4. JNI shared library
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Ensure internal headers for vocab and batching are available
target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/common
    ${LLAMA_ROOT}/ggml/include
    ${LLAMA_ROOT}/ggml/src
)

find_library(log-lib log)
find_library(vulkan-lib vulkan)

# Link both targets; ggml contains the actual Vulkan backend implementation
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
    # Optimization for ARM64 Android devices
    $<$<CONFIG:Release>:-O3 -march=armv8-a+dotprod+crypto>
)
