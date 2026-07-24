# Original android_virtual_cam algorithm (from HookMain.java)

Source: https://github.com/w2016561536/android_virtual_cam  
We reimplement this under **pure Magisk Zygisk + LSPlant** (no LSPosed manager).

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
   - First call: read preview size, start `VideoToFrames` decoder on `virtual.mp4` → NV21 into `data_buffer`
   - Every call: `System.arraycopy(data_buffer, 0, data, 0, min(len))`
3. `VideoToFrames` uses MediaCodec, output format NV21 (must match preview WxH).

### C. takePicture

Replace JPEG/YUV result with a still derived from the video (or `1000.bmp` in some builds).

## Camera2 path

1. Hook `CameraManager.openCamera(...)`
2. Hook `CaptureRequest.Builder.addTarget/removeTarget/build`
3. Redirect surfaces to virtual `SurfaceTexture` + `MediaPlayer` for preview
4. `ImageReader` path: decode video frames into NV21/JPEG for readers
5. Session callbacks (`onConfigured`, etc.) rewired to virtual surfaces

## Why pure Zygisk needs LSPlant

Original code uses Xposed `findAndHookMethod` on **Java** methods.  
Zygisk alone can:
- gate processes (done)
- PLT/JNI-native hook (limited for Camera Java API)

**LSPlant** provides ART Java method hooking without installing LSPosed.  
We init LSPlant inside our Zygisk `.so` and hook the same targets listed above.

## Port status in this project

| Piece | Status |
|-------|--------|
| Paths + flags + APK automation | Done |
| Zygisk process gate + status | Done |
| Compiled Zygisk `.so` in Magisk zip | Done (CI) |
| MediaPlayer surface replacement | Blueprint (needs LSPlant) |
| NV21 callback injection | Blueprint (needs LSPlant + VideoToFrames) |
| Camera2 surface redirect | Blueprint |
