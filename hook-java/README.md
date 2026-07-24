# Java hook layer (LSPlant target)

These classes mirror original `com.example.vcam.HookMain` / `VideoToFrames`  
but are written to be driven from Zygisk+LSPlant (no Xposed API).

Build into a dex later; load from Zygisk after `lsplant::Init`.

## Camera1Hooks.kt (logic summary)

```
when app calls Camera.setPreviewTexture(st):
  if enabled && has virtual.mp4 && !disable:
    replace st with fake SurfaceTexture
    later MediaPlayer plays virtual.mp4 onto the real surface the app keeps

when app registers PreviewCallback:
  start MediaCodec decode of virtual.mp4 → NV21 ring buffer
  on each onPreviewFrame: copy latest NV21 into the callback byte[]
```

See `docs/ORIGINAL_HOOK_ALGORITHM.md` for exact original behavior.
