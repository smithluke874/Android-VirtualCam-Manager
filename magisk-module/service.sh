#!/system/bin/sh
# Late start service - keep paths healthy after boot
mkdir -p /data/media/0/DCIM/Camera1
chmod 775 /data/media/0/DCIM/Camera1
chown media_rw:media_rw /data/media/0/DCIM/Camera1 2>/dev/null || true
mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
