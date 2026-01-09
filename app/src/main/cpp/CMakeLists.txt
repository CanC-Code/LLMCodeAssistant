# LLMCodeAssistant/app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.18)
project(LLMCodeAssistantCpp)

# -------------------- Android settings --------------------
if(ANDROID)
    set(CMAKE_CXX_STANDARD 17)
    set(CMAKE_CXX_STANDARD_REQUIRED ON)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions -frtti")
endif()

# -------------------- Include llama.cpp submodule --------------------
set(LLAMA_SUBMODULE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp")

if(EXISTS "${LLAMA_SUBMODULE_DIR}/CMakeLists.txt")
    message(STATUS "Adding llama.cpp submodule")
    add_subdirectory("${LLAMA_SUBMODULE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama_cpp_build")
else()
    message(WARNING "llama.cpp submodule not found! Run 'git submodule update --init --recursive'")
endif()

# -------------------- JNI library --------------------
add_library(
    llama_jni
    SHARED
    llama_jni.cpp
)

# Include paths for JNI and llama.cpp
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(TARGET llama) # only if llama.cpp was added
    target_link_libraries(llama_jni PRIVATE llama)
endif()

# Link log library for Android
if(ANDROID)
    find_library(log-lib log)
    target_link_libraries(llama_jni PRIVATE ${log-lib})
endif()