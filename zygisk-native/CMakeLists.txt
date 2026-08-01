cmake_minimum_required(VERSION 3.18)
project(virtualcam LANGUAGES C CXX ASM)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ArtHook static library (vendored)
add_subdirectory(third_party/arthook)

add_library(virtualcam SHARED jni/main.cpp)

target_include_directories(virtualcam PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/arthook/include
)

target_compile_options(virtualcam PRIVATE
    -Wall
    -fvisibility=hidden
    -fno-rtti
    -fno-exceptions
    -O2
)

target_link_libraries(virtualcam PRIVATE
    arthook::arthook
    log
    dl
    android
)

# Output name expected by Magisk packaging: libs/<abi>/libvirtualcam.so
set_target_properties(virtualcam PROPERTIES
    OUTPUT_NAME "virtualcam"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/libs/${ANDROID_ABI}"
)
