# CMake build configuration for ScrollGuard with llama.cpp integration
cmake_minimum_required(VERSION 3.22.1)

project("scrollguard")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure build type
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Platform-specific optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+fp+simd")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a+fp+simd")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv7-a -mfpu=neon")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv7-a -mfpu=neon")
endif()

# Optimization flags
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -ffast-math")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -DNDEBUG -ffast-math")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O0 -g")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O0 -g")
endif()

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# llama.cpp configuration
set(GGML_USE_CPU ON)
set(GGML_BUILD_TESTS OFF)
set(GGML_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)

# Include llama.cpp directories
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# Check if llama.cpp exists (for development without submodule)
if(EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(STATUS "Found llama.cpp at ${LLAMA_CPP_DIR}")
    
    # Add llama.cpp subdirectory
    add_subdirectory(${LLAMA_CPP_DIR} llama_cpp_build)
    
    # Get llama.cpp include directories
    include_directories(${LLAMA_CPP_DIR}/include)
    include_directories(${LLAMA_CPP_DIR})
    include_directories(${LLAMA_CPP_DIR}/ggml/include)
    
    set(LLAMA_CPP_AVAILABLE TRUE)
else()
    message(WARNING "llama.cpp not found at ${LLAMA_CPP_DIR}. Building with placeholder implementation.")
    set(LLAMA_CPP_AVAILABLE FALSE)
endif()

# Include our custom headers
include_directories(${CMAKE_SOURCE_DIR}/include)

# Our JNI wrapper sources
set(SCROLLGUARD_SOURCES
    native/native_bridge.cpp
    jni/llama_jni.cpp
    jni/content_classifier.cpp
    jni/model_loader.cpp
)

# Create our JNI library
add_library(
    scrollguard-native
    SHARED
    ${SCROLLGUARD_SOURCES}
)

# Compiler definitions
target_compile_definitions(scrollguard-native PRIVATE
    GGML_USE_CPU
    $<$<BOOL:${LLAMA_CPP_AVAILABLE}>:LLAMA_CPP_AVAILABLE>
    $<$<CONFIG:Debug>:DEBUG>
    $<$<CONFIG:Release>:NDEBUG>
)

# Link libraries
if(LLAMA_CPP_AVAILABLE)
    target_link_libraries(
        scrollguard-native
        llama
        ggml
        ${log-lib}
        ${android-lib}
    )
    message(STATUS "Linking with actual llama.cpp libraries")
else()
    target_link_libraries(
        scrollguard-native
        ${log-lib}
        ${android-lib}
    )
    message(STATUS "Linking without llama.cpp (placeholder mode)")
endif()

# Set additional properties
set_target_properties(scrollguard-native PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)