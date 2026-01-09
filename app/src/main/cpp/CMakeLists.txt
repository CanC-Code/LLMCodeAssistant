# Minimum CMake version
cmake_minimum_required(VERSION 3.18)

# Set project name
project(LLMCodeAssistantJni LANGUAGES C CXX)

# Specify the Android ABI and API level (handled by Gradle usually)
# set(CMAKE_SYSTEM_NAME Android)
# set(CMAKE_ANDROID_ARCH_ABI arm64-v8a)
# set(CMAKE_ANDROID_NDK /path/to/ndk)
# set(CMAKE_ANDROID_API 34)

# Enable C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define source files for the library
set(SRC_FILES
    llama_jni.cpp
    ${CMAKE_SOURCE_DIR}/../../../../external/llama.cpp
)

# Create shared library for JNI
add_library(
    llm                 # Library name
    SHARED              # Build as shared library
    ${SRC_FILES}        # Source files
)

# Find the JNI package
find_package(JNI REQUIRED)

# Include JNI headers
include_directories(${JNI_INCLUDE_DIRS})

# Link libraries
target_link_libraries(
    llm
    ${log-lib}          # Android log library
)

# Define Android log library (used in llama_jni.cpp)
find_library(log-lib log)