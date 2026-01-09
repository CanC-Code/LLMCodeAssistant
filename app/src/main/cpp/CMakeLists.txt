# File: app/src/main/cpp/CMakeLists.txt

cmake_minimum_required(VERSION 3.18)

project(LLMCodeAssistantJni LANGUAGES C CXX)

# ----------------------
# C++ standard
# ----------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ----------------------
# Source files
# ----------------------
# Add all .cpp files in the current directory
file(GLOB CPP_SRC "${CMAKE_CURRENT_SOURCE_DIR}/*.cpp")

# Add all .cpp files in external/ relative to app/src/main/cpp/
file(GLOB EXTERNAL_CPP "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/*.cpp")

# Combine source files
set(SRC_FILES ${CPP_SRC} ${EXTERNAL_CPP})

# ----------------------
# Shared library
# ----------------------
add_library(
    llm                 # library name
    SHARED              # type
    ${SRC_FILES}        # sources
)

# ----------------------
# Include directories
# ----------------------
target_include_directories(
    llm
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}                     # cpp/
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external # external/
)

# ----------------------
# Link Android log library
# ----------------------
find_library(log-lib log)

target_link_libraries(
    llm
    ${log-lib}
)

# ----------------------
# Optional: compiler flags (optimization)
# ----------------------
target_compile_options(llm PRIVATE
    -O3                  # Optimize for speed
    -fexceptions         # Enable exceptions
    -frtti               # Enable RTTI
)