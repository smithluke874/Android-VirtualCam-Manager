#!/system/bin/sh
# VirtualCam Manager - Pure Magisk only (no LSPosed / no Xposed)
SKIPUNZIP=1

ui_print "- VirtualCam Manager v1.3.0"
ui_print "- Pure Magisk + single controller APK"
ui_print "- No LSPosed required"

unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2

ui_print "- Creating /DCIM/Camera1 (classic VCAM path)"
mkdir -p /data/media/0/DCIM/Camera1
chmod 775 /data/media/0/DCIM/Camera1
chown media_rw:media_rw /data/media/0/DCIM/Camera1 2>/dev/null || true
mkdir -p /sdcard/DCIM/Camera1 2>/dev/null || true

mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
touch /data/adb/virtualcam/module_installed
echo "v1.3.0" > /data/adb/virtualcam/version

# Zygisk native library directory (place arm64-v8a.so / armeabi-v7a.so here when built)
mkdir -p $MODPATH/zygisk
# Marker so module is recognized as Zygisk-capable once real .so lands
touch $MODPATH/zygisk/.placeholder

ui_print "- Camera1 paths + Zygisk skeleton ready"
ui_print " "
ui_print "- Next steps:"
ui_print "  1. Install VirtualCam Manager APK"
ui_print "  2. Grant root"
ui_print "  3. Place virtual.mp4 (Media tab)"
ui_print "  4. Toggle flags (Settings)"
ui_print " "
ui_print "- Note: Frame injection requires Zygisk native"
ui_print "  hooks (under development). Paths/flags work now."
ui_print "- Done. Reboot recommended."
