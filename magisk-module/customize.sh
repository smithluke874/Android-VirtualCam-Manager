#!/system/bin/sh
# VirtualCam Manager install

ui_print "- VirtualCam Manager (pure Magisk + Zygisk, NO LSPosed)"
ui_print "- Ensure Magisk → settings → Zygisk is ON, then reboot"

mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
echo -n 0 > /data/adb/virtualcam/enabled
echo -n "1.7.0" > /data/adb/virtualcam/module_version
chmod 644 /data/adb/virtualcam/* 2>/dev/null

# Zygisk .so presence
if [ -f "$MODPATH/zygisk/arm64-v8a.so" ] || [ -f "$MODPATH/zygisk/armeabi-v7a.so" ]; then
  ui_print "- Zygisk libraries packaged"
else
  ui_print "! Warning: no zygisk/*.so — rebuild CI artifact"
fi

ui_print "- After reboot: install Manager APK, grant root, pick media"
