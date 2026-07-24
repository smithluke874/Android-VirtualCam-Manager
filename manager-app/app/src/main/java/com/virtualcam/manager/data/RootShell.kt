package com.virtualcam.manager.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around libsu for all privileged operations.
 * Always use this instead of Runtime.exec to avoid deadlocks.
 */
object RootShell {

    init {
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        Shell.getShell().isRoot
    }

    suspend fun exec(vararg commands: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(*commands).exec()
    }

    suspend fun execAndGetOutput(vararg commands: String): List<String> = withContext(Dispatchers.IO) {
        Shell.cmd(*commands).exec().out
    }

    suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        val result = Shell.cmd("id").exec()
        result.isSuccess && result.out.any { it.contains("uid=0") }
    }
}
