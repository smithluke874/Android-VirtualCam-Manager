#!/system/bin/sh
SKIPUNZIP=1

ui_print "- VirtualCam Manager v1.4.0"
ui_print "- Pure Magisk + single controller APK"
ui_print "- No LSPosed required"

unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2

ui_print "- Creating /DCIM/Camera1"
mkdir -p /data/media/0/DCIM/Camera1
chmod 775 /data/media/0/DCIM/Camera1
chown media_rw:media_rw /data/media/0/DCIM/Camera1 2>/dev/null || true
mkdir -p /sdcard/DCIM/Camera1 2>/dev/null || true

mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
touch /data/adb/virtualcam/module_installed
echo "v1.4.0" > /data/adb/virtualcam/version
# Default to enabled so APK one-tap works after first flash
echo 1 > /data/adb/virtualcam/enabled

# Zygisk libs (present when CI built them)
mkdir -p $MODPATH/zygisk

ui_print "- Paths + Zygisk dir ready"
ui_print "- Install the APK, grant root, toggle ON"
ui_print "- Done. Reboot recommended."
