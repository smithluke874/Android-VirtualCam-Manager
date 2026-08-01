package com.virtualcam.manager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Camera1 directories, virtual.mp4 placement, and flag files.
 * Also mirrors video into /data/adb/virtualcam/ so the Zygisk native
 * resolve_video() always finds a readable path (Camera1 can be scoped
 * or delayed on some ROMs).
 */
class FileSystemRepository {

    companion object {
        const val GLOBAL_CAMERA1 = "/storage/emulated/0/DCIM/Camera1"
        const val CONTROL_DIR = "/data/adb/virtualcam"
        const val VIRTUAL_MP4 = "virtual.mp4"
        const val FLAG_DISABLE = "disable.jpg"
        const val FLAG_PRIVATE_DIR = "private_dir.jpg"
        const val FLAG_NO_SILENT = "no-silent.jpg"
        const val FLAG_NO_TOAST = "no_toast.jpg"
        const val FLAG_FORCE_SHOW = "force_show.jpg"

        val IMPORT_CANDIDATES = listOf(
            "/storage/emulated/0/Download/virtual.mp4",
            "/storage/emulated/0/Downloads/virtual.mp4",
            "/sdcard/Download/virtual.mp4",
            "/sdcard/Downloads/virtual.mp4",
            "/storage/emulated/0/DCIM/virtual.mp4",
            "/storage/emulated/0/Movies/virtual.mp4"
        )
    }

    suspend fun ensureGlobalCamera1(): Boolean = withContext(Dispatchers.IO) {
        val result = RootShell.exec(
            "mkdir -p $GLOBAL_CAMERA1",
            "chmod 775 $GLOBAL_CAMERA1",
            "chown media_rw:media_rw $GLOBAL_CAMERA1 2>/dev/null || true"
        )
        result.isSuccess
    }

    suspend fun ensureControlDir(): Boolean = withContext(Dispatchers.IO) {
        RootShell.exec(
            "mkdir -p $CONTROL_DIR",
            "chmod 755 $CONTROL_DIR"
        ).isSuccess
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

    /**
     * Install virtual.mp4 into both classic Camera1 path and the control-plane
     * path that Zygisk native resolve_video() prefers as fallback.
     */
    suspend fun placeVirtualVideo(sourcePath: String, usePrivate: Boolean = false, packageName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val dir = if (usePrivate && packageName != null) {
                ensurePrivateCamera1(packageName)
                getPrivateCamera1Path(packageName)
            } else {
                ensureGlobalCamera1()
                GLOBAL_CAMERA1
            }
            ensureControlDir()
            val dest = "$dir/$VIRTUAL_MP4"
            val controlDest = "$CONTROL_DIR/$VIRTUAL_MP4"
            val result = RootShell.exec(
                "cp -f \"$sourcePath\" \"$dest\"",
                "chmod 644 \"$dest\"",
                "chown media_rw:media_rw \"$dest\" 2>/dev/null || true",
                "cp -f \"$sourcePath\" \"$controlDest\"",
                "chmod 644 \"$controlDest\"",
                "sync"
            )
            result.isSuccess
        }

    suspend fun importVirtualFromDownloads(): String? = withContext(Dispatchers.IO) {
        ensureGlobalCamera1()
        for (candidate in IMPORT_CANDIDATES) {
            val exists = RootShell.exec("[ -f \"$candidate\" ] && echo 1 || echo 0")
                .out.firstOrNull()?.trim() == "1"
            if (exists) {
                val ok = placeVirtualVideo(candidate)
                if (ok) return@withContext candidate
            }
        }
        null
    }

    suspend fun hasVirtualVideo(usePrivate: Boolean = false, packageName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val dir = if (usePrivate && packageName != null) getPrivateCamera1Path(packageName) else GLOBAL_CAMERA1
            val a = RootShell.exec("[ -f $dir/$VIRTUAL_MP4 ] && echo 1 || echo 0")
                .out.firstOrNull()?.trim() == "1"
            val b = RootShell.exec("[ -f $CONTROL_DIR/$VIRTUAL_MP4 ] && echo 1 || echo 0")
                .out.firstOrNull()?.trim() == "1"
            a || b
        }

    suspend fun removeVirtualVideo(usePrivate: Boolean = false, packageName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val dir = if (usePrivate && packageName != null) getPrivateCamera1Path(packageName) else GLOBAL_CAMERA1
            RootShell.exec(
                "rm -f $dir/$VIRTUAL_MP4",
                "rm -f $CONTROL_DIR/$VIRTUAL_MP4"
            ).isSuccess
        }

    suspend fun listCamera1Contents(): List<String> = withContext(Dispatchers.IO) {
        ensureGlobalCamera1()
        RootShell.exec("ls -la $GLOBAL_CAMERA1 2>/dev/null").out
    }
}
