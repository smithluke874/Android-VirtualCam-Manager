#!/system/bin/sh
ui_print "- VirtualCam Manager v1.16.0 (pure Magisk + Zygisk, NO LSPosed)"
ui_print "- Magisk → Settings → Zygisk ON, then reboot"
mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
echo -n 0 > /data/adb/virtualcam/enabled
echo -n "1.16.0" > /data/adb/virtualcam/module_version
echo -n "1.16.0" > /data/adb/virtualcam/version
touch /data/adb/virtualcam/module_installed
chmod 644 /data/adb/virtualcam/* 2>/dev/null
if [ -f "$MODPATH/zygisk/arm64-v8a.so" ] || [ -f "$MODPATH/zygisk/armeabi-v7a.so" ]; then
  ui_print "- Zygisk libraries packaged"
else
  ui_print "! Warning: no zygisk/*.so — use CI artifact"
fi
ui_print "- After reboot: install Manager APK → grant root → pick media → ON"
