LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE     := virtualcam
LOCAL_SRC_FILES  := main.cpp
LOCAL_LDLIBS     := -llog
LOCAL_CFLAGS     := -std=c++17 -fno-exceptions -fno-rtti
include $(BUILD_SHARED_LIBRARY)
