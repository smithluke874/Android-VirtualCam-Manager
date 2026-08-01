package com.virtualcam.manager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PrerequisiteStatus(
    val rootAvailable: Boolean = false,
    val magiskPresent: Boolean = false,
    val moduleInstalled: Boolean = false,
    val camera1Ready: Boolean = false,
    val moduleControlDir: Boolean = false,
    val moduleVersion: String? = null,
    val zygiskLibPresent: Boolean = false,
    val hookStatus: String? = null,
    val lastHookPkg: String? = null,
    val vcamEnabled: Boolean = false,
    val hasVirtualVideo: Boolean = false,
    /** Live telemetry written by native GL path */
    val decoderFrames: Int = 0,
    val textureId: Long = 0,
    val bindHits: Int = 0
) {
    val allPassed: Boolean
        get() = rootAvailable && magiskPresent && camera1Ready
}

class PrerequisiteChecker(
    private val fileSystem: FileSystemRepository = FileSystemRepository()
) {

    suspend fun check(): PrerequisiteStatus = withContext(Dispatchers.IO) {
        val root = RootShell.isRootAvailable()

        val magisk = RootShell.exec("[ -d /data/adb/magisk ] && echo 1").out.firstOrNull()?.trim() == "1" ||
                RootShell.exec("pm path com.topjohnwu.magisk").isSuccess ||
                RootShell.exec("which magisk").isSuccess

        val modulePathCheck = RootShell.exec(
            "[ -d /data/adb/modules/virtualcam_manager ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1"

        val markerCheck = RootShell.exec(
            "[ -f /data/adb/virtualcam/module_installed ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1"

        val moduleInstalled = modulePathCheck || markerCheck

        val version = RootShell.exec(
            "cat /data/adb/virtualcam/version 2>/dev/null"
        ).out.firstOrNull()?.trim()

        val zygiskLib = RootShell.exec(
            "[ -f /data/adb/modules/virtualcam_manager/zygisk/arm64-v8a.so ] && echo 1 || " +
                    "[ -f /data/adb/modules/virtualcam_manager/zygisk/armeabi-v7a.so ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1"

        val hookStatus = RootShell.exec(
            "cat /data/adb/virtualcam/hook_status 2>/dev/null"
        ).out.firstOrNull()?.trim()

        val lastPkg = RootShell.exec(
            "cat /data/adb/virtualcam/last_hook_pkg 2>/dev/null"
        ).out.firstOrNull()?.trim()

        val enabled = RootShell.exec(
            "[ -f /data/adb/virtualcam/enabled ] && cat /data/adb/virtualcam/enabled || echo 0"
        ).out.firstOrNull()?.trim() == "1"

        val hasVideo = fileSystem.hasVirtualVideo()
        val camera1 = fileSystem.ensureGlobalCamera1()
        val control = RootShell.exec("mkdir -p /data/adb/virtualcam && echo 1").isSuccess

        // Live telemetry from native (optional files — zero if absent)
        val frames = RootShell.exec(
            "cat /data/adb/virtualcam/decoder_frames 2>/dev/null || echo 0"
        ).out.firstOrNull()?.trim()?.toIntOrNull() ?: 0

        val texId = RootShell.exec(
            "cat /data/adb/virtualcam/texture_id 2>/dev/null || echo 0"
        ).out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L

        val hits = RootShell.exec(
            "cat /data/adb/virtualcam/bind_hits 2>/dev/null || echo 0"
        ).out.firstOrNull()?.trim()?.toIntOrNull() ?: 0

        PrerequisiteStatus(
            rootAvailable = root,
            magiskPresent = magisk,
            moduleInstalled = moduleInstalled,
            camera1Ready = camera1,
            moduleControlDir = control,
            moduleVersion = version,
            zygiskLibPresent = zygiskLib,
            hookStatus = hookStatus,
            lastHookPkg = lastPkg,
            vcamEnabled = enabled,
            hasVirtualVideo = hasVideo,
            decoderFrames = frames,
            textureId = texId,
            bindHits = hits
        )
    }
}
