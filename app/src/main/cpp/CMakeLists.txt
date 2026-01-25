cmake_minimum_required(VERSION 3.24.1)
project(llama_jni LANGUAGES C CXX ASM)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to llama.cpp source
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

# [span_10](start_span)Options for llama.cpp[span_10](end_span)
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# [span_11](start_span)Create the JNI library[span_11](end_span)
add_library(llama_jni SHARED llama_jni.cpp)

# [span_12](start_span)Include directories for the JNI target[span_12](end_span)
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/ggml/include
)

# [span_13](start_span)Link against the llama and ggml targets[span_13](end_span)
find_library(log-lib log)
target_link_libraries(llama_jni 
    llama 
    ggml 
    ${log-lib}
)
