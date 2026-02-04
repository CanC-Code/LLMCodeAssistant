cmake_minimum_required(VERSION 3.22.1)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ----------------------------
# 1. llama.cpp path configuration
# ----------------------------
# Aligned with your specific project structure seen in logs
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

if(NOT EXISTS ${LLAMA_ROOT}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_ROOT}")
endif()

# ----------------------------
# 2. GGML / Vulkan configuration (MUST be before add_subdirectory)
# ----------------------------
set(GGML_VULKAN ON CACHE BOOL "Use Vulkan" FORCE)

# CRITICAL FIX: Embed shaders to solve "Error opening file: ... .spv"
# This ensures shaders are part of the binary rather than external files.
set(GGML_VULKAN_SHADER_EMBED ON CACHE BOOL "Embed Vulkan shaders" FORCE)

set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# ----------------------------
# 3. Vulkan Header Resolution
# ----------------------------
if(ANDROID)
    # The log shows 'vulkan/vulkan.hpp' not found. 
    # Use the path passed from your GitHub Actions workflow.
    if(Vulkan_INCLUDE_DIR)
        include_directories(${Vulkan_INCLUDE_DIR})
    else()
        # Fallback to NDK sysroot if the workflow variable isn't set
        include_directories(${ANDROID_NDK}/toolchains/llvm/prebuilt/${ANDROID_HOST_TAG}/sysroot/usr/include)
    endif()
endif()

# Add the llama.cpp project
add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# ----------------------------
# 4. JNI shared library
# ----------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Standardized include paths to ensure all llama/ggml headers are found
target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/common
    ${LLAMA_ROOT}/ggml/include
)

# Find Android-specific libraries
find_library(log-lib log)
find_library(vulkan-lib vulkan)

# Link against 'llama' and 'ggml' targets
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

set_target_properties(llama_jni PROPERTIES
    OUTPUT_NAME "llama_jni"
)
