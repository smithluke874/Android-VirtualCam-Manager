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
    val decoderFrames: Int = 0,
    val textureId: Long = 0,
    val bindHits: Int = 0,
    val pathMode: String? = null,
    val diag: String? = null,
    val lastError: String? = null,
    val capabilities: String? = null
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
        val moduleInstalled = RootShell.exec(
            "[ -d /data/adb/modules/virtualcam_manager ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1" || RootShell.exec(
            "[ -f /data/adb/virtualcam/module_installed ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1"
        val version = RootShell.exec("cat /data/adb/virtualcam/version 2>/dev/null").out.firstOrNull()?.trim()
        val zygiskLib = RootShell.exec(
            "[ -f /data/adb/modules/virtualcam_manager/zygisk/arm64-v8a.so ] && echo 1 || " +
            "[ -f /data/adb/modules/virtualcam_manager/zygisk/armeabi-v7a.so ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1"
        val hookStatus = RootShell.exec("cat /data/adb/virtualcam/hook_status 2>/dev/null").out.firstOrNull()?.trim()
        val lastPkg = RootShell.exec("cat /data/adb/virtualcam/last_hook_pkg 2>/dev/null").out.firstOrNull()?.trim()
        val enabled = RootShell.exec(
            "[ -f /data/adb/virtualcam/enabled ] && cat /data/adb/virtualcam/enabled || echo 0"
        ).out.firstOrNull()?.trim() == "1"
        val hasVideo = fileSystem.hasVirtualVideo()
        val camera1 = fileSystem.ensureGlobalCamera1()
        val control = RootShell.exec("mkdir -p /data/adb/virtualcam && echo 1").isSuccess
        fun cat(p: String) = RootShell.exec("cat $p 2>/dev/null").out.firstOrNull()?.trim()
        val frames = cat("/data/adb/virtualcam/decoder_frames")?.toIntOrNull() ?: 0
        val texId = cat("/data/adb/virtualcam/texture_id")?.toLongOrNull() ?: 0L
        val hits = cat("/data/adb/virtualcam/bind_hits")?.toIntOrNull() ?: 0
        val pathMode = cat("/data/adb/virtualcam/path_mode")?.takeIf { it.isNotEmpty() }
        val diag = cat("/data/adb/virtualcam/diag")?.takeIf { it.isNotEmpty() }
        val lastError = cat("/data/adb/virtualcam/last_error")?.takeIf { it.isNotEmpty() }
        val capabilities = cat("/data/adb/virtualcam/capabilities")?.takeIf { it.isNotEmpty() }
        PrerequisiteStatus(
            rootAvailable = root, magiskPresent = magisk, moduleInstalled = moduleInstalled,
            camera1Ready = camera1, moduleControlDir = control, moduleVersion = version,
            zygiskLibPresent = zygiskLib, hookStatus = hookStatus, lastHookPkg = lastPkg,
            vcamEnabled = enabled, hasVirtualVideo = hasVideo, decoderFrames = frames,
            textureId = texId, bindHits = hits, pathMode = pathMode,
            diag = diag, lastError = lastError, capabilities = capabilities
        )
    }
    suspend fun requestProbe(): Boolean = withContext(Dispatchers.IO) {
        RootShell.exec(
            "mkdir -p /data/adb/virtualcam",
            "echo 1 > /data/adb/virtualcam/probe_request",
            "chmod 644 /data/adb/virtualcam/probe_request"
        ).isSuccess
    }
}
