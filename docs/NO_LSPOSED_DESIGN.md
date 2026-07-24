# Pure Magisk + Single APK Design

## Why no LSPosed

The original android_virtual_cam APK is an Xposed module (`com.example.vcam.HookMain`).  
It only works when an Xposed framework (LSPosed, etc.) injects it into target processes.

**User requirement: only Magisk module + one APK, no LSPosed.**

## New architecture

1. **Magisk module**
   - Creates and maintains the exact filesystem layout the original VCAM expected (`/DCIM/Camera1/virtual.mp4` + sentinel .jpg flags).
   - Provides `/data/adb/virtualcam/` control directory for the APK.
   - Zygisk-ready (placeholder for future native Camera hooks).

2. **Single sole APK** (`com.virtualcam.manager`)
   - The only user-facing application.
   - Uses libsu (root) to:
     - Ensure directories
     - Copy / remove `virtual.mp4`
     - Create / delete the flag files that control behaviour
     - Report status
   - Modern Compose UI.

## What works today without Xposed hooks

- All path and flag management (identical to original).
- Video file placement.
- Module enable/disable via flag files.

## What requires Zygisk native work (next phase)

- Actual interception of Camera1 / Camera2 preview surfaces and feeding frames from `virtual.mp4`.
  This is the part the original `HookMain` did via Xposed.
  A Zygisk module can implement the same hooks in native code for latest Android.

Until the native Zygisk component is complete, the module + APK fully manage the environment the original exploit used. The APK is the sole control surface.
