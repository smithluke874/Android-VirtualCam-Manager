package com.virtualcam.manager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PrerequisiteStatus(
    val rootAvailable: Boolean = false,
    val magiskPresent: Boolean = false,
    val moduleInstalled: Boolean = false,
    val camera1Ready: Boolean = false,
    val moduleControlDir: Boolean = false,
    val moduleVersion: String? = null
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

        // Detect our module specifically
        val modulePathCheck = RootShell.exec(
            "[ -d /data/adb/modules/virtualcam_manager ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1"

        val markerCheck = RootShell.exec(
            "[ -f /data/adb/virtualcam/module_installed ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1"

        val moduleInstalled = modulePathCheck || markerCheck

        val version = RootShell.exec(
            "cat /data/adb/virtualcam/version 2>/dev/null || cat /data/adb/modules/virtualcam_manager/module.prop 2>/dev/null | grep version= | head -1"
        ).out.firstOrNull()?.trim()?.removePrefix("version=")

        val camera1 = fileSystem.ensureGlobalCamera1()
        val control = RootShell.exec("mkdir -p /data/adb/virtualcam && echo 1").isSuccess

        PrerequisiteStatus(
            rootAvailable = root,
            magiskPresent = magisk,
            moduleInstalled = moduleInstalled,
            camera1Ready = camera1,
            moduleControlDir = control,
            moduleVersion = version
        )
    }
}
