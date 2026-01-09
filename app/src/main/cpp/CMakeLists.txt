# =========================================================
# app/src/main/cpp/CMakeLists.txt
# Build script for LLM JNI library
# =========================================================

# Minimum CMake version
cmake_minimum_required(VERSION 3.18)

# Project name
project(llm_android LANGUAGES C CXX)

# Require C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# -----------------------------------------------------------------------------
# Add your LLM library
# -----------------------------------------------------------------------------

# Path to external source files
set(LLAMA_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/external/llama.cpp
    # Add other .cpp files here if needed, e.g.,
    # ${CMAKE_CURRENT_SOURCE_DIR}/external/llama_helpers.cpp
)

add_library(
    llm          # library name
    STATIC       # or SHARED if you want .so
    ${LLAMA_SOURCES}
)

# -----------------------------------------------------------------------------
# Include directories
# -----------------------------------------------------------------------------
target_include_directories(
    llm PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}          # your cpp/ root
    ${CMAKE_CURRENT_SOURCE_DIR}/external
)

# -----------------------------------------------------------------------------
# Compiler flags
# -----------------------------------------------------------------------------
if (ANDROID)
    # Recommended flags for Android
    target_compile_options(llm PRIVATE
        -Wall
        -Wextra
        -O3
        -fexceptions
        -frtti
    )
endif()

# -----------------------------------------------------------------------------
# Link any required libraries here
# -----------------------------------------------------------------------------
# Example:
# find_library(log-lib log)
# target_link_libraries(llm ${log-lib})

# -----------------------------------------------------------------------------
# End of CMakeLists.txt
# -----------------------------------------------------------------------------