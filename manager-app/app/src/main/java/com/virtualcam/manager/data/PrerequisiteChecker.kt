package com.virtualcam.manager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PrerequisiteStatus(
    val rootAvailable: Boolean = false,
    val magiskPresent: Boolean = false,
    val camera1Ready: Boolean = false,
    val moduleControlDir: Boolean = false
) {
    val allPassed: Boolean
        get() = rootAvailable && magiskPresent && camera1Ready
}

class PrerequisiteChecker(
    private val fileSystem: FileSystemRepository = FileSystemRepository()
) {

    suspend fun check(): PrerequisiteStatus = withContext(Dispatchers.IO) {
        val root = RootShell.isRootAvailable()
        val magisk = RootShell.exec("pm path com.topjohnwu.magisk").isSuccess ||
                RootShell.exec("[ -d /data/adb/magisk ] && echo 1").out.firstOrNull()?.trim() == "1"
        val camera1 = fileSystem.ensureGlobalCamera1()
        val control = RootShell.exec("mkdir -p /data/adb/virtualcam && echo 1").isSuccess

        PrerequisiteStatus(
            rootAvailable = root,
            magiskPresent = magisk,
            camera1Ready = camera1,
            moduleControlDir = control
        )
    }
}
