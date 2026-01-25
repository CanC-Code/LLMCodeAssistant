cmake_minimum_required(VERSION 3.24.1)
project(llama_jni LANGUAGES C CXX ASM) # Added ASM for hardware acceleration

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define path to llama.cpp
set(LLAMA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)

# 1. CONFIGURE llama.cpp build options BEFORE adding the subdirectory
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)
set(GGML_USE_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# 2. ADD the llama.cpp project
add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

# 3. CREATE your JNI library target
add_library(llama_jni SHARED llama_jni.cpp)

# 4. NOW you can specify include directories for the target
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_ROOT}/include
    ${LLAMA_ROOT}/src
    ${LLAMA_ROOT}/ggml/include
    ${LLAMA_ROOT}/ggml/src
)

# 5. LINK libraries
find_library(log-lib log)
# Note: In recent llama.cpp versions, 'llama' and 'ggml' are the primary targets
target_link_libraries(llama_jni 
    llama 
    ggml 
    ${log-lib}
)
