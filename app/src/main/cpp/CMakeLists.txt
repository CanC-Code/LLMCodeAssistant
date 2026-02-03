cmake_minimum_required(VERSION 3.18.1)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ----------------------------
# llama.cpp path
# ----------------------------
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

if(NOT EXISTS ${LLAMA_ROOT}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_ROOT}")
endif()

# ----------------------------
# llama.cpp configuration
# ----------------------------
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Optimization: Enable Vulkan for Android GPU acceleration
set(GGML_VULKAN ON CACHE BOOL "" FORCE) 
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)

# Optimization: Modern Android devices are arm64-v8a; 
# llamafile is safe and faster here than on older ARMv7.
set(GGML_USE_LLAMAFILE ON CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# ----------------------------
# JNI shared library
# ----------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/common  # Added for access to common utilities if needed
)

# Android libraries
find_library(log-lib log)
find_library(vulkan-lib vulkan) # Find Vulkan lib for the linker

target_link_libraries(llama_jni
    llama
    ${log-lib}
    ${vulkan-lib}
)

target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
    -fexceptions
    $<$<CONFIG:Release>:-O3 -march=armv8-a+dotprod+crypto> # Targeted optimizations
)

set_target_properties(llama_jni PROPERTIES
    OUTPUT_NAME "llama_jni"
)
