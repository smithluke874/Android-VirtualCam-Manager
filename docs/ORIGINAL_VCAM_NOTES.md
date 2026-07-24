# Original VCAM Notes

Source of truth: the classic `com.example.vcam` / android_virtual_cam Xposed module.

## Paths (preserved 1:1)

- Global: `/storage/emulated/0/DCIM/Camera1/`
- Private (when private_dir.jpg exists or app lacks storage permission): `/storage/emulated/0/Android/data/<pkg>/files/Camera1/`

## Files

| File              | Meaning                                      |
|-------------------|----------------------------------------------|
| virtual.mp4       | The video that replaces the camera feed      |
| disable.jpg       | Temporarily disable the module               |
| private_dir.jpg   | Force private directory mode                 |
| no-silent.jpg     | Disable silent mode / force sound?           |
| no_toast.jpg      | Suppress toast messages                      |
| force_show.jpg    | Force show certain UI / resolution toast     |

These exact names and locations are what the original HookMain looks for.
