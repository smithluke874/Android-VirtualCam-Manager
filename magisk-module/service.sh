#!/system/bin/sh
# Late-start service: keep paths healthy after boot + media daemon up

MODDIR=${0%/*}

# Wait a bit for /data/media to be fully mounted
sleep 3

mkdir -p /data/media/0/DCIM/Camera1
chmod 775 /data/media/0/DCIM/Camera1
chown media_rw:media_rw /data/media/0/DCIM/Camera1 2>/dev/null || true

# Fuse /sdcard view (best-effort)
mkdir -p /sdcard/DCIM/Camera1 2>/dev/null || true

mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam

# Mark that late service has run (APK can read this)
date +%s > /data/adb/virtualcam/last_service 2>/dev/null || true

restorecon -R /data/media/0/DCIM/Camera1 2>/dev/null || true
