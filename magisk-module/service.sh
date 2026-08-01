#!/system/bin/sh
# VirtualCam Manager — late_start service
MODDIR=${0%/*}
sleep 8
mkdir -p /data/adb/virtualcam
mkdir -p /storage/emulated/0/DCIM/Camera1 2>/dev/null
chmod 775 /storage/emulated/0/DCIM/Camera1 2>/dev/null
restorecon -RF /storage/emulated/0/DCIM/Camera1 2>/dev/null
restorecon -RF /data/adb/virtualcam 2>/dev/null
chmod 644 /data/adb/virtualcam/enabled 2>/dev/null
chmod 644 /data/adb/virtualcam/hook_status 2>/dev/null
chmod 644 /data/adb/virtualcam/last_hook_pkg 2>/dev/null
echo -n "1.10.0" > /data/adb/virtualcam/module_version
echo -n "1.10.0" > /data/adb/virtualcam/version
chmod 644 /data/adb/virtualcam/module_version 2>/dev/null
chmod 644 /data/adb/virtualcam/version 2>/dev/null
