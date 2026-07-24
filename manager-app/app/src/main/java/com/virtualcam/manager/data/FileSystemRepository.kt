package com.virtualcam.manager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Camera1 directories, virtual.mp4 placement, and flag files
 * exactly as the original VCAM module expects.
 */
class FileSystemRepository {

    companion object {
        const val GLOBAL_CAMERA1 = "/storage/emulated/0/DCIM/Camera1"
        const val VIRTUAL_MP4 = "virtual.mp4"
        const val FLAG_DISABLE = "disable.jpg"
        const val FLAG_PRIVATE_DIR = "private_dir.jpg"
        const val FLAG_NO_SILENT = "no-silent.jpg"
        const val FLAG_NO_TOAST = "no_toast.jpg"
        const val FLAG_FORCE_SHOW = "force_show.jpg"
    }

    suspend fun ensureGlobalCamera1(): Boolean = withContext(Dispatchers.IO) {
        val result = RootShell.exec(
            "mkdir -p $GLOBAL_CAMERA1",
            "chmod 775 $GLOBAL_CAMERA1",
            "chown media_rw:media_rw $GLOBAL_CAMERA1 2>/dev/null || true"
        )
        result.isSuccess
    }

    suspend fun getPrivateCamera1Path(packageName: String): String {
        return "/storage/emulated/0/Android/data/$packageName/files/Camera1"
    }

    suspend fun ensurePrivateCamera1(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val path = getPrivateCamera1Path(packageName)
        val result = RootShell.exec(
            "mkdir -p $path",
            "chmod 775 $path"
        )
        result.isSuccess
    }

    suspend fun isFlagEnabled(flag: String, usePrivate: Boolean = false, packageName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val dir = if (usePrivate && packageName != null) getPrivateCamera1Path(packageName) else GLOBAL_CAMERA1
            val result = RootShell.exec("[ -f $dir/$flag ] && echo 1 || echo 0")
            result.out.firstOrNull()?.trim() == "1"
        }

    suspend fun setFlag(flag: String, enabled: Boolean, usePrivate: Boolean = false, packageName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val dir = if (usePrivate && packageName != null) getPrivateCamera1Path(packageName) else GLOBAL_CAMERA1
            val cmd = if (enabled) {
                "touch $dir/$flag && chmod 644 $dir/$flag"
            } else {
                "rm -f $dir/$flag"
            }
            RootShell.exec(cmd).isSuccess
        }

    suspend fun placeVirtualVideo(sourcePath: String, usePrivate: Boolean = false, packageName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val dir = if (usePrivate && packageName != null) {
                ensurePrivateCamera1(packageName)
                getPrivateCamera1Path(packageName)
            } else {
                ensureGlobalCamera1()
                GLOBAL_CAMERA1
            }
            val dest = "$dir/$VIRTUAL_MP4"
            val result = RootShell.exec(
                "cp -f \"$sourcePath\" \"$dest\"",
                "chmod 644 \"$dest\"",
                "sync"
            )
            result.isSuccess
        }

    suspend fun hasVirtualVideo(usePrivate: Boolean = false, packageName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val dir = if (usePrivate && packageName != null) getPrivateCamera1Path(packageName) else GLOBAL_CAMERA1
            val result = RootShell.exec("[ -f $dir/$VIRTUAL_MP4 ] && echo 1 || echo 0")
            result.out.firstOrNull()?.trim() == "1"
        }

    suspend fun removeVirtualVideo(usePrivate: Boolean = false, packageName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val dir = if (usePrivate && packageName != null) getPrivateCamera1Path(packageName) else GLOBAL_CAMERA1
            RootShell.exec("rm -f $dir/$VIRTUAL_MP4").isSuccess
        }
}
