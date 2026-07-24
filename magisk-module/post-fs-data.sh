#!/system/bin/sh
# VirtualCam Manager — early boot paths

MODDIR=${0%/*}

mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam

# Default disabled until APK enables
if [ ! -f /data/adb/virtualcam/enabled ]; then
  echo -n 0 > /data/adb/virtualcam/enabled
fi
chmod 644 /data/adb/virtualcam/enabled 2>/dev/null

# Ensure Camera1 exists early (may be re-created after unlock)
mkdir -p /storage/emulated/0/DCIM/Camera1 2>/dev/null
chmod 775 /storage/emulated/0/DCIM/Camera1 2>/dev/null
