cmake_minimum_required(VERSION 3.18.1)

project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Paths
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

if(NOT EXISTS ${LLAMA_ROOT}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_ROOT}")
endif()

# Add llama.cpp submodule
add_subdirectory(
    ${LLAMA_ROOT}
    ${CMAKE_BINARY_DIR}/llama
)

# JNI shared library
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Include llama headers
target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}
)

# Android log
find_library(log-lib log)

# Link libraries
target_link_libraries(llama_jni
    llama
    ${log-lib}
)

# Compile flags
target_compile_options(llama_jni PRIVATE
    -Wall -Wextra -Wpedantic
    -fexceptions
    $<$<CONFIG:Release>:-O3>
)

# Define symbols for shared backend
target_compile_definitions(llama_jni PRIVATE
    LLAMA_SHARED
    GGML_BACKEND_SHARED
)

# Output name
set_target_properties(llama_jni PROPERTIES OUTPUT_NAME "llama_jni")