#!/system/bin/sh
# Early boot: ensure classic VCAM paths exist before apps start

MODDIR=${0%/*}

# Primary emulated storage path (works on modern Android)
mkdir -p /data/media/0/DCIM/Camera1
chmod 775 /data/media/0/DCIM/Camera1
chown media_rw:media_rw /data/media/0/DCIM/Camera1 2>/dev/null || true

# Control dir for APK + future Zygisk
mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam

# Restore SELinux context if restorecon is available (Android 10+)
restorecon -R /data/media/0/DCIM/Camera1 2>/dev/null || true
