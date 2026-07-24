#!/system/bin/sh
MODDIR=${0%/*}
sleep 3
mkdir -p /data/media/0/DCIM/Camera1
chmod 775 /data/media/0/DCIM/Camera1
chown media_rw:media_rw /data/media/0/DCIM/Camera1 2>/dev/null || true
mkdir -p /sdcard/DCIM/Camera1 2>/dev/null || true
mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
date +%s > /data/adb/virtualcam/last_service 2>/dev/null || true
restorecon -R /data/media/0/DCIM/Camera1 2>/dev/null || true
