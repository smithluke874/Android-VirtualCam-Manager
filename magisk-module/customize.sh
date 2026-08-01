#!/system/bin/sh
ui_print "- VirtualCam Manager v2.0.0-dev (pure Magisk + Zygisk, NO LSPosed)"
ui_print "- Native OpenGL path — Magisk → Settings → Zygisk ON, then reboot"
mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
echo -n 0 > /data/adb/virtualcam/enabled
echo -n "2.0.0-dev" > /data/adb/virtualcam/module_version
echo -n "2.0.0-dev" > /data/adb/virtualcam/version
touch /data/adb/virtualcam/module_installed
chmod 644 /data/adb/virtualcam/* 2>/dev/null

# Prefer ABI-matched ShadowHook for dlopen from process
ABI=$(getprop ro.product.cpu.abi)
if [ -f "$MODPATH/libs/$ABI/libshadowhook.so" ]; then
  cp -f "$MODPATH/libs/$ABI/libshadowhook.so" "$MODPATH/libshadowhook.so"
  ui_print "- ShadowHook installed for $ABI"
elif [ -f "$MODPATH/libshadowhook.so" ]; then
  ui_print "- ShadowHook present (generic)"
else
  ui_print "! ShadowHook missing — GL hooks will stay pending until provided"
fi

if [ -f "$MODPATH/zygisk/arm64-v8a.so" ] || [ -f "$MODPATH/zygisk/armeabi-v7a.so" ]; then
  ui_print "- Zygisk libraries packaged"
else
  ui_print "! Warning: no zygisk/*.so — use CI artifact"
fi
ui_print "- After reboot: install Manager APK → grant root → pick media → ON"
ui_print "- Status is experimental until real apps show the virtual feed"
