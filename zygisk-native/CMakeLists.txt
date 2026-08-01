cmake_minimum_required(VERSION 3.18)
project(virtualcam LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# --- 16 KB page size (Android 15+ kernels) ---
set(VCAM_PAGE_FLAGS "-Wl,-z,max-page-size=16384")

add_library(virtualcam SHARED
    jni/main.cpp
    jni/gl_hooks.cpp
)

target_include_directories(virtualcam PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

# Optional ShadowHook: if third_party/shadowhook exists, link it
set(SHADOWHOOK_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/shadowhook")
if(EXISTS "${SHADOWHOOK_DIR}/include/shadowhook.h")
    message(STATUS "ShadowHook found — enabling VCAM_HAS_SHADOWHOOK")
    target_include_directories(virtualcam PRIVATE ${SHADOWHOOK_DIR}/include)
    target_compile_definitions(virtualcam PRIVATE VCAM_HAS_SHADOWHOOK=1)
    if(EXISTS "${SHADOWHOOK_DIR}/lib/${ANDROID_ABI}/libshadowhook.a")
        target_link_libraries(virtualcam PRIVATE
            ${SHADOWHOOK_DIR}/lib/${ANDROID_ABI}/libshadowhook.a)
    elseif(EXISTS "${SHADOWHOOK_DIR}/lib/${ANDROID_ABI}/libshadowhook.so")
        target_link_libraries(virtualcam PRIVATE
            ${SHADOWHOOK_DIR}/lib/${ANDROID_ABI}/libshadowhook.so)
    else()
        message(STATUS "ShadowHook headers only — runtime dlopen expected")
    endif()
else()
    message(STATUS "ShadowHook not present — scaffold builds without inline hooks")
endif()

target_compile_options(virtualcam PRIVATE
    -Wall
    -fvisibility=hidden
    -fno-rtti
    -fno-exceptions
    -O2
)

target_link_options(virtualcam PRIVATE ${VCAM_PAGE_FLAGS})

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
