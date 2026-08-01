# Original android_virtual_cam algorithm (from HookMain.java)

Source: https://github.com/w2016561536/android_virtual_cam  
We reimplement this under **pure Magisk Zygisk + ArtHook** (no LSPosed manager).

## Control files (same paths we already automate)

| Path under `/DCIM/Camera1/` | Effect |
|-----------------------------|--------|
| `virtual.mp4` | Replacement video |
| `disable.jpg` | Hard off |
| `no_toast.jpg` | Suppress toasts |
| `private_dir.jpg` | Prefer app-private Camera1 dir |
| `force_show.jpg` / `no-silent.jpg` | UX / audio related |

## Camera1 path (most common)

### A. Surface / Texture preview (what the user *sees*)

1. Hook `Camera.setPreviewTexture(SurfaceTexture)`
2. If `virtual.mp4` exists and no `disable.jpg`:
   - Save original texture
   - Replace arg with `new SurfaceTexture(10)` (fake)
3. Hook `Camera.startPreview()` / `setPreviewDisplay(SurfaceHolder)`:
   - Create `MediaPlayer`, setDataSource(`virtual.mp4`), setSurface(fake or holder), setLooping(true), start()
4. Result: preview surface shows the video, not the real camera.

### B. PreviewCallback path (byte[] frames for apps that process buffers)

1. Hook `setPreviewCallback` / `setPreviewCallbackWithBuffer` / `setOneShotPreviewCallback`
2. Hook the callback's `onPreviewFrame(byte[] data, Camera camera)`:
   - First call: read preview size, start MediaCodec decoder on `virtual.mp4` → NV21 into buffer
   - Every call: copy latest NV21 frame into `data`
3. Continuous loop: AMediaExtractor + AMediaCodec, scale/convert to preview WxH NV21.
   Pattern (moving bar) is used only if the decoder cannot start.

### C. takePicture

Replace JPEG/YUV result with a still derived from the video (or `1000.bmp` in some builds).

## Camera2 path

1. Hook `CameraManager.openCamera(...)`
2. Hook `CaptureRequest.Builder.addTarget/removeTarget/build`
3. Redirect surfaces to virtual `SurfaceTexture` + `MediaPlayer` for preview
4. `ImageReader` path: decode video frames into NV21/JPEG for readers
5. Session callbacks (`onConfigured`, etc.) rewired to virtual surfaces

## Why pure Zygisk needs ART hooks

Original code uses Xposed `findAndHookMethod` on **Java** methods.  
Zygisk alone can:
- gate processes (done)
- PLT/JNI-native hook (limited for Camera Java API)

**ArtHook** provides ART Java method hooking without installing LSPosed.  
We init ArtHook inside our Zygisk `.so` and hook the same targets listed above.

## Port status in this project

| Piece | Status |
|-------|--------|
| Paths + flags + APK automation | **Done** |
| Zygisk process gate + status | **Done** |
| Compiled Zygisk `.so` in Magisk zip | **Done** (CI multi-ABI) |
| MediaPlayer surface replacement | **Done** (v1.13 ArtHook) |
| NV21 callback injection (pattern) | **Done** (v1.15) |
| MediaCodec continuous video→NV21 | **Done** (v1.16) |
| Camera2 surface redirect | Blueprint |
