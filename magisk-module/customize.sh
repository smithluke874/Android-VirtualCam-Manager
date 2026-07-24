#!/system/bin/sh
# VirtualCam Manager - Pure Magisk only (no LSPosed / no Xposed)
# Prepares the exact filesystem layout expected by the classic android_virtual_cam (VCAM) exploit.

SKIPUNZIP=1

ui_print "- VirtualCam Manager v1.2.0"
ui_print "- Pure Magisk + single controller APK"
ui_print "- No LSPosed required"

# Extract module files
unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2

# Exact paths the original VCAM used
ui_print "- Creating /DCIM/Camera1 (classic VCAM path)"
mkdir -p /data/media/0/DCIM/Camera1
chmod 775 /data/media/0/DCIM/Camera1
chown media_rw:media_rw /data/media/0/DCIM/Camera1 2>/dev/null || true

# Compatibility mirror
mkdir -p /sdcard/DCIM/Camera1 2>/dev/null || true

# Control directory for APK <-> module communication and future Zygisk config
mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
# Placeholder for future Zygisk native config
mkdir -p $MODPATH/zygisk 2>/dev/null || true

ui_print "- Camera1 paths + control dir ready"
ui_print " "
ui_print "- Next steps:"
ui_print "  1. Install the single VirtualCam Manager APK"
ui_print "  2. Grant root to the APK"
ui_print "  3. Place virtual.mp4 and toggle flags from the app"
ui_print " "
ui_print "- Done. Reboot recommended."
