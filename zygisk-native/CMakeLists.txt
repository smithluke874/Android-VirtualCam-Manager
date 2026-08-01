cmake_minimum_required(VERSION 3.18)
project(virtualcam LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# 16 KB pages for Android 15+
set(VCAM_PAGE_FLAGS "-Wl,-z,max-page-size=16384")

add_library(virtualcam SHARED
    jni/main.cpp
    jni/gl_hooks.cpp
)

target_include_directories(virtualcam PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

target_compile_options(virtualcam PRIVATE
    -Wall
    -fvisibility=hidden
    -fno-rtti
    -fno-exceptions
    -O2
)

target_link_options(virtualcam PRIVATE ${VCAM_PAGE_FLAGS})

# ShadowHook is dlopen'd at runtime — no link dependency.
target_link_libraries(virtualcam PRIVATE
    log
    dl
    android
    mediandk
    GLESv2
    EGL
)

set_target_properties(virtualcam PROPERTIES
    OUTPUT_NAME "virtualcam"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/libs/${ANDROID_ABI}"
)
