cmake_minimum_required(VERSION 3.24.1)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ----------------------------
# Path Configuration
# ----------------------------
# Adjusted to match standard Android NDK project structure
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/external/llama.cpp)

# Fallback check if user hasn't initialized submodules correctly
if(NOT EXISTS ${LLAMA_ROOT}/include/llama.h)
    message(FATAL_ERROR "llama.cpp headers not found. Check path: ${LLAMA_ROOT}")
endif()

# ----------------------------
# llama.cpp Core Build Settings
# ----------------------------
# We force static linking of llama.cpp into our JNI shared library
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Optimization for Android ARM64
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(GGML_USE_LLAMAFILE OFF CACHE BOOL "" FORCE) # Avoid ARMv7/FP16 incompatibilities
set(GGML_FATAL_ERRORS ON CACHE BOOL "" FORCE)

# Optional: Enable dot product instructions for newer ARM chips (v8.2+)
# Only enable if you are sure about your target hardware; otherwise, stay generic.
# set(GGML_ARM_FMA ON CACHE BOOL "" FORCE)

# Add llama.cpp as a sub-project
add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama_build EXCLUDE_FROM_ALL)

# ----------------------------
# JNI Shared Library Build
# ----------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Include directories for llama and the new ggml headers
target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/ggml/include
)

# Android System Libraries
find_library(log-lib log)
find_library(android-lib android)

# Linkage: llama.cpp now requires linking to both llama and ggml
target_link_libraries(llama_jni
    llama
    ggml
    ${log-lib}
    ${android-lib}
)

# ----------------------------
# Performance Optimizations
# ----------------------------
target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
    -fexceptions
    -frtti
    # Release-specific optimizations
    $<$<CONFIG:Release>:-O3>
    $<$<CONFIG:Release>:-ffast-math>
    $<$<CONFIG:Release>:-flto>
)

# Ensure the library is named exactly what System.loadLibrary expects
set_target_properties(llama_jni PROPERTIES
    OUTPUT_NAME "llama_jni"
)
