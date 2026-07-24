Zygisk native hooks (planned)
==============================

This directory is where Magisk loads Zygisk modules from:
  arm64-v8a.so
  armeabi-v7a.so
  x86_64.so (emulator)

Original android_virtual_cam (Xposed) hooked:
  - android.hardware.Camera (Camera1)
      PreviewCallback, PictureCallback, Parameters, setPreviewTexture
  - android.hardware.camera2.* (Camera2)
      CameraManager, CameraDevice, CaptureRequest, CaptureSession
  - android.graphics.SurfaceTexture
  - android.media.Image / ImageReader

It read video from:
  /storage/emulated/0/DCIM/Camera1/virtual.mp4
  (or private per-app path when private_dir.jpg exists)

Our pure-Magisk design will implement the same interception in native
Zygisk code so NO LSPosed / Xposed is required.

Until the .so files are built and placed here, the module still:
  - Creates Camera1 paths at boot
  - Lets the companion APK manage virtual.mp4 + flag files
