cmake_minimum_required(VERSION 3.10.2)

project(llama_jni LANGUAGES C CXX)

# ----------------------------------
# Compiler settings
# ----------------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)   # Needed for shared libraries

# Optional: Enable warnings
if (CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
    add_compile_options(-Wall -Wextra -Wno-unused-parameter -Wno-missing-field-initializers)
endif()

# ----------------------------------
# Paths
# ----------------------------------
# CMakeLists.txt is in app/src/main/cpp
# llama.cpp submodule is at repo_root/external/llama.cpp
set(LLAMA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp")

if (NOT EXISTS "${LLAMA_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_ROOT}")
endif()

# ----------------------------------
# Add llama.cpp (upstream CMake)
# ----------------------------------
add_subdirectory(
    ${LLAMA_ROOT}
    ${CMAKE_BINARY_DIR}/llama
)

# ----------------------------------
# JNI library
# ----------------------------------
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_ROOT}/include
)

# Android log library
find_library(log-lib log REQUIRED)

# Link llama library and Android log
target_link_libraries(llama_jni
    llama
    ${log-lib}
)

# Optional: define macros for Android
target_compile_definitions(llama_jni PRIVATE
    ANDROID
)