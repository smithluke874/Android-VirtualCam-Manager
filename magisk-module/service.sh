#!/system/bin/sh
MODDIR=${0%/*}
sleep 3
mkdir -p /data/media/0/DCIM/Camera1
chmod 775 /data/media/0/DCIM/Camera1
chown media_rw:media_rw /data/media/0/DCIM/Camera1 2>/dev/null || true
mkdir -p /sdcard/DCIM/Camera1 2>/dev/null || true
mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
# Keep enabled flag if APK already set it; otherwise default on
[ -f /data/adb/virtualcam/enabled ] || echo 1 > /data/adb/virtualcam/enabled
date +%s > /data/adb/virtualcam/last_service 2>/dev/null || true
restorecon -R /data/media/0/DCIM/Camera1 2>/dev/null || true
