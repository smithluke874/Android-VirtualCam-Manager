#!/bin/bash
set -e
cd zygisk-native/jni
if [ -f ../../patches/v1.16.0/main_v116.patch ]; then
  patch -p1 -d ../.. < ../../patches/v1.16.0/main_v116.patch || true
fi
if [ -f ../../patches/v1.16.0/nv21_hooks.p1.cpp ] && [ -f ../../patches/v1.16.0/nv21_hooks.p2.cpp ]; then
  cat ../../patches/v1.16.0/nv21_hooks.p1.cpp ../../patches/v1.16.0/nv21_hooks.p2.cpp > nv21_hooks.cpp
  echo "Assembled MediaCodec nv21_hooks.cpp ($(wc -c < nv21_hooks.cpp) bytes)"
fi
if [ -f ../../patches/v1.16.0/nv21_hooks.h ]; then
  cp ../../patches/v1.16.0/nv21_hooks.h nv21_hooks.h
  echo "Installed MediaCodec nv21_hooks.h"
fi
grep -n 'NdkMediaCodec\|1\.16\.0\|decoderLoop\|setVideoPath' main.cpp nv21_hooks.cpp nv21_hooks.h || true
