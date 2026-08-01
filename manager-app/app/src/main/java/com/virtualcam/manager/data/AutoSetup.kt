package com.virtualcam.manager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-shot automation: prepare the pure-Magisk VirtualCam environment
 * from the APK with root. No manual shell steps required.
 */
class AutoSetup(
    private val fs: FileSystemRepository = FileSystemRepository()
) {

    data class Result(
        val rootOk: Boolean,
        val camera1Ok: Boolean,
        val controlDirOk: Boolean,
        val moduleDetected: Boolean,
        val enabledWritten: Boolean,
        val message: String
    )

    suspend fun runFullSetup(enable: Boolean = true): Result = withContext(Dispatchers.IO) {
        val root = RootShell.isRootAvailable()
        if (!root) {
            return@withContext Result(
                rootOk = false,
                camera1Ok = false,
                controlDirOk = false,
                moduleDetected = false,
                enabledWritten = false,
                message = "Root not granted. Open Magisk and allow this app."
            )
        }

        val camera1 = fs.ensureGlobalCamera1()
        val control = fs.ensureControlDir()

        val module = RootShell.exec(
            "[ -d /data/adb/modules/virtualcam_manager ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1" ||
            RootShell.exec(
                "[ -f /data/adb/virtualcam/module_installed ] && echo 1 || echo 0"
            ).out.firstOrNull()?.trim() == "1"

        val enabledCmd = if (enable) {
            listOf(
                "echo 1 > /data/adb/virtualcam/enabled",
                "echo 'mode=global' > /data/adb/virtualcam/config",
                "echo 'path=/storage/emulated/0/DCIM/Camera1/virtual.mp4' >> /data/adb/virtualcam/config",
                "rm -f /storage/emulated/0/DCIM/Camera1/disable.jpg",
                // Keep control-plane copy in sync if Camera1 has the file
                "[ -f /storage/emulated/0/DCIM/Camera1/virtual.mp4 ] && " +
                    "cp -f /storage/emulated/0/DCIM/Camera1/virtual.mp4 /data/adb/virtualcam/virtual.mp4 || true"
            )
        } else {
            listOf(
                "echo 0 > /data/adb/virtualcam/enabled",
                "touch /storage/emulated/0/DCIM/Camera1/disable.jpg",
                "chmod 644 /storage/emulated/0/DCIM/Camera1/disable.jpg"
            )
        }
        val enabledWritten = RootShell.exec(*enabledCmd.toTypedArray()).isSuccess

        RootShell.exec("date +%s > /data/adb/virtualcam/last_setup")

        val msg = buildString {
            append(if (enable) "VirtualCam ENABLED. " else "VirtualCam DISABLED. ")
            if (!module) append("Flash Magisk module + reboot for Zygisk path. ")
            if (enable) append("Open any camera app to test.")
            else append("Camera apps will see the real camera again.")
        }

        Result(
            rootOk = true,
            camera1Ok = camera1,
            controlDirOk = control,
            moduleDetected = module,
            enabledWritten = enabledWritten,
            message = msg
        )
    }

    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        val r = RootShell.exec(
            "[ -f /data/adb/virtualcam/enabled ] && cat /data/adb/virtualcam/enabled || echo 0"
        )
        r.out.firstOrNull()?.trim() == "1"
    }
}
