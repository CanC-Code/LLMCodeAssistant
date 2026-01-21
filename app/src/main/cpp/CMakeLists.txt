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

set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(GGML_USE_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_USE_ACCELERATE OFF CACHE BOOL "" FORCE)
set(GGML_USE_METAL OFF CACHE BOOL "" FORCE)
set(GGML_USE_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_USE_CUDA OFF CACHE BOOL "" FORCE)

# CRITICAL: Disable llamafile (uses FP16 intrinsics not available in ARMv7)
set(GGML_USE_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_USE_CPU_REPACK OFF CACHE BOOL "" FORCE)

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
)

# Android log
find_library(log-lib log)

target_link_libraries(llama_jni
    llama
    ${log-lib}
)

target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -fexceptions
    $<$<CONFIG:Release>:-O3>
)

set_target_properties(llama_jni PROPERTIES
    OUTPUT_NAME "llama_jni"
)