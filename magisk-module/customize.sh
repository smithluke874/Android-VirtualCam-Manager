#!/system/bin/sh
SKIPUNZIP=1

ui_print "- VirtualCam Manager v1.5.0"
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
echo "v1.5.0" > /data/adb/virtualcam/version
echo 1 > /data/adb/virtualcam/enabled
echo "idle" > /data/adb/virtualcam/hook_status

mkdir -p $MODPATH/zygisk

# Report whether native libs are present
if [ -f $MODPATH/zygisk/arm64-v8a.so ]; then
  ui_print "- Zygisk native: arm64-v8a.so OK"
elif [ -f $MODPATH/zygisk/armeabi-v7a.so ]; then
  ui_print "- Zygisk native: armeabi-v7a.so OK"
else
  ui_print "- Zygisk native: placeholder (rebuild CI for .so)"
fi

ui_print "- Install APK → grant root → toggle ON"
ui_print "- Done. Reboot recommended."
