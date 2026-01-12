cmake_minimum_required(VERSION 3.10.2)
project("llama_jni")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------- Absolute paths for llama.cpp ----------------
get_filename_component(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../" ABSOLUTE)
set(LLAMA_DIR "${REPO_ROOT}/external/llama.cpp")
set(LLAMA_INCLUDE "${LLAMA_DIR}/include")
set(GGML_INCLUDE "${LLAMA_DIR}/ggml/include")  # optional

# ---------------- Only build JNI library if llama.h exists ----------------
if(EXISTS "${LLAMA_INCLUDE}/llama.h")
    message(STATUS "Building llama_jni: llama.h found at ${LLAMA_INCLUDE}")

    add_library(llama_jni SHARED llama_jni.cpp)

    target_include_directories(llama_jni PRIVATE
        "${LLAMA_INCLUDE}"
        "${GGML_INCLUDE}"
    )

    # Link standard Android libraries
    find_library(log-lib log)
    target_link_libraries(llama_jni ${log-lib})

    # Output per ABI
    set_target_properties(llama_jni PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/../jniLibs/${ANDROID_ABI}"
    )

else()
    message(WARNING "Skipping llama_jni build: ${LLAMA_INCLUDE}/llama.h not found. The JNI library will not be built.")
endif()