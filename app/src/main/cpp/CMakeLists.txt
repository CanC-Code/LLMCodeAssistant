cmake_minimum_required(VERSION 3.10.2)
project("llama_jni")

# Use C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- Paths ---

# Current CMakeLists.txt is in app/src/main/cpp
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp")
set(LLAMA_INCLUDE "${LLAMA_DIR}/include")
set(GGML_INCLUDE "${LLAMA_DIR}/ggml/include")  # optional

# --- Verify llama.h exists ---
if(NOT EXISTS "${LLAMA_INCLUDE}/llama.h")
    message(FATAL_ERROR "Cannot find llama.h at ${LLAMA_INCLUDE}. JNI build skipped.")
endif()

message(STATUS "Building llama_jni: llama.h found at ${LLAMA_INCLUDE}")

# --- Sources ---
file(GLOB LLAMA_SOURCES "${LLAMA_DIR}/*.cpp")
set(JNI_SOURCES llama_jni.cpp)

# --- Add shared JNI library ---
add_library(llama_jni SHARED ${JNI_SOURCES} ${LLAMA_SOURCES})

# --- Include directories ---
target_include_directories(llama_jni PRIVATE
    "${LLAMA_INCLUDE}"
)

# Add GGML include if it exists
if(EXISTS "${GGML_INCLUDE}")
    target_include_directories(llama_jni PRIVATE "${GGML_INCLUDE}")
endif()

# --- Link Android log library ---
find_library(log-lib log)
target_link_libraries(llama_jni ${log-lib} c++_shared)

# --- Output directory for Gradle ---
set_target_properties(llama_jni PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/../jniLibs/${ANDROID_ABI}"
)