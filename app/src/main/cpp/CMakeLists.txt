cmake_minimum_required(VERSION 3.22.1)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ----------------------------
# 1. llama.cpp path configuration
# ----------------------------
# The log shows the build is looking in app/src/main/cpp/external/llama.cpp.
# If your submodule is at the project root, the path is ../../../../external/llama.cpp.
set(LLAMA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp")

# Check if the path is valid; if not, fallback to a local 'external' folder
if(NOT EXISTS "${LLAMA_ROOT}/CMakeLists.txt")
    set(LLAMA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/external/llama.cpp")
endif()

if(NOT EXISTS "${LLAMA_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found. Checked: 
    1. ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp
    2. ${CMAKE_CURRENT_SOURCE_DIR}/external/llama.cpp
    Please verify where your 'external' folder is located.")
endif()

# ----------------------------
# 2. GGML / Vulkan Flags (Must be set BEFORE add_subdirectory)
# ----------------------------
set(GGML_VULKAN ON CACHE BOOL "Use Vulkan" FORCE)

# CRITICAL FIX: Embed shaders to prevent "Error opening file: ... .spv"
# This fixes the Ninja build errors where .spv files are not found
set(GGML_VULKAN_SHADER_EMBED ON CACHE BOOL "Embed Vulkan shaders" FORCE)

set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# ----------------------------
# 3. Vulkan Header Resolution
# ----------------------------
if(ANDROID)
    # Use the Vulkan include directory passed from GitHub Actions (-DVulkan_INCLUDE_DIR)
    # [span_2](start_span)This provides the C++ vulkan.hpp headers which llama.cpp requires[span_2](end_span)
    if(Vulkan_INCLUDE_DIR)
        include_directories(${Vulkan_INCLUDE_DIR})
    endif()
    
    # Fallback/Additional NDK headers
    include_directories(${ANDROID_NDK}/toolchains/llvm/prebuilt/${ANDROID_HOST_TAG}/sysroot/usr/include)
endif()

# Add llama.cpp project
add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# ----------------------------
# 4. JNI shared library
# ----------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Fix: Added 'src' and 'ggml/src' to includes for internal header access
target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/common
    ${LLAMA_ROOT}/ggml/include
    ${LLAMA_ROOT}/ggml/src
)

# Find Android libraries
find_library(log-lib log)
find_library(vulkan-lib vulkan)

# Fix: Explicitly link both 'llama' and 'ggml' targets
target_link_libraries(llama_jni
    llama
    ggml
    ${log-lib}
    ${vulkan-lib}
)

# Optimization and ARM-specific features
target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
    -fexceptions
    $<$<CONFIG:Release>:-O3 -march=armv8-a+dotprod+crypto>
)

set_target_properties(llama_jni PROPERTIES
    OUTPUT_NAME "llama_jni"
)
