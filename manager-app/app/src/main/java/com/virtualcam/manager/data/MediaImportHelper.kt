package com.virtualcam.manager.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Picks image or video from the system picker, converts images to a short
 * looping H.264 MP4, and installs the result as /DCIM/Camera1/virtual.mp4
 * via root so Zygisk + classic VCAM paths can use it.
 */
class MediaImportHelper(
    private val fs: FileSystemRepository = FileSystemRepository()
) {

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val isImage: Boolean = false,
        val destPath: String? = null
    )

    suspend fun importFromUri(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri)
                ?: guessMime(uri)
                ?: "application/octet-stream"
            val isImage = mime.startsWith("image/")
            val isVideo = mime.startsWith("video/")

            if (!isImage && !isVideo) {
                return@withContext ImportResult(false, "Unsupported type: $mime")
            }

            val cacheDir = File(context.cacheDir, "vcam_import").apply { mkdirs() }
            val workFile = File(cacheDir, if (isVideo) "picked_video.bin" else "picked_image.bin")

            // Stream content into app-private cache (works with Photo Picker / SAF)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(workFile).use { out -> input.copyTo(out) }
            } ?: return@withContext ImportResult(false, "Cannot read selected file")

            if (workFile.length() < 32) {
                return@withContext ImportResult(false, "Selected file is empty")
            }

            val mp4ToInstall: File = if (isVideo) {
                // Ensure .mp4 extension for downstream tools
                val named = File(cacheDir, "virtual_src.mp4")
                workFile.copyTo(named, overwrite = true)
                named
            } else {
                // Decode image → short looping H.264 mp4
                val bmp = decodeBitmap(workFile)
                    ?: return@withContext ImportResult(false, "Could not decode image")
                val outMp4 = File(cacheDir, "virtual_from_image.mp4")
                encodeStillToMp4(bmp, outMp4)
                // Also drop a still for takePicture-style paths
                saveStillForTakePicture(bmp, cacheDir)
                bmp.recycle()
                outMp4
            }

            if (!mp4ToInstall.exists() || mp4ToInstall.length() < 100) {
                return@withContext ImportResult(false, "Failed to prepare MP4")
            }

            // Root-install into classic VCAM path
            val ok = fs.placeVirtualVideo(mp4ToInstall.absolutePath)
            if (!ok) {
                return@withContext ImportResult(false, "Root copy to Camera1 failed. Grant root and retry.")
            }

            // Image still (optional, best-effort)
            val still = File(cacheDir, "1000.bmp")
            if (still.exists()) {
                RootShell.exec(
                    "cp -f \"${still.absolutePath}\" /storage/emulated/0/DCIM/Camera1/1000.bmp",
                    "chmod 644 /storage/emulated/0/DCIM/Camera1/1000.bmp"
                )
            }

            // Auto-enable control plane so user only picks media once
            RootShell.exec(
                "mkdir -p /data/adb/virtualcam",
                "echo 1 > /data/adb/virtualcam/enabled",
                "rm -f /storage/emulated/0/DCIM/Camera1/disable.jpg",
                "echo 'source=app_picker' > /data/adb/virtualcam/last_import"
            )

            val kind = if (isImage) "image (encoded to looping MP4)" else "video"
            ImportResult(
                success = true,
                message = "Installed $kind as virtual.mp4\n" +
                        "/DCIM/Camera1/virtual.mp4\n" +
                        "VirtualCam enabled. Open a camera app to use it.",
                isImage = isImage,
                destPath = FileSystemRepository.GLOBAL_CAMERA1 + "/" + FileSystemRepository.VIRTUAL_MP4
            )
        } catch (t: Throwable) {
            ImportResult(false, "Import error: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun guessMime(uri: Uri): String? {
        val name = uri.lastPathSegment ?: return null
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private fun decodeBitmap(file: File): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        // Cap resolution for encoder stability / preview match
        val maxSide = 1280
        val w = bmp.width
        val h = bmp.height
        if (w > maxSide || h > maxSide) {
            val scale = maxSide.toFloat() / maxOf(w, h)
            val nw = (w * scale).toInt().coerceAtLeast(2) and 1.inv() // even
            val nh = (h * scale).toInt().coerceAtLeast(2) and 1.inv()
            val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
            if (scaled != bmp) bmp.recycle()
            bmp = scaled
        }
        // Encoder needs even dimensions
        val ew = bmp.width and 1.inv()
        val eh = bmp.height and 1.inv()
        if (ew != bmp.width || eh != bmp.height) {
            val cropped = Bitmap.createBitmap(bmp, 0, 0, ew, eh)
            if (cropped != bmp) bmp.recycle()
            bmp = cropped
        }
        return bmp
    }

    private fun saveStillForTakePicture(bmp: Bitmap, cacheDir: File) {
        try {
            val f = File(cacheDir, "1000.bmp")
            FileOutputStream(f).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out) // PNG ok; filename matches original convention
            }
        } catch (_: Throwable) { }
    }

    /**
     * Encode a still image as a short looping H.264 MP4 (NV12 → AVC).
     * Duration ~3s @ 10fps so MediaPlayer / VideoToFrames have content.
     */
    private fun encodeStillToMp4(src: Bitmap, outFile: File, durationSec: Int = 3, fps: Int = 10) {
        val width = src.width and 1.inv()
        val height = src.height and 1.inv()
        val frameCount = durationSec * fps
        val mime = "video/avc"
        val bitRate = (width * height * 4).coerceIn(500_000, 4_000_000)

        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        if (outFile.exists()) outFile.delete()
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false

        val nv12 = bitmapToNv12(src, width, height)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputFrames = 0
        var outputDone = false

        while (!outputDone) {
            if (inputFrames < frameCount) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)!!
                    inBuf.clear()
                    inBuf.put(nv12)
                    val ptsUs = inputFrames * 1_000_000L / fps
                    codec.queueInputBuffer(inIndex, 0, nv12.size, ptsUs, 0)
                    inputFrames++
                }
            } else {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }

            var outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            while (outIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
                val outBuf = codec.getOutputBuffer(outIndex)!!
                if (bufferInfo.size > 0) {
                    if (!muxerStarted) {
                        val newFormat = codec.outputFormat
                        track = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(track, outBuf, bufferInfo)
                }
                codec.releaseOutputBuffer(outIndex, false)
                outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
            }
        }

        codec.stop()
        codec.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
    }

    /** ARGB_8888 bitmap → NV12 (YUV420 semi-planar) */
    private fun bitmapToNv12(bmp: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bmp.getPixels(argb, 0, width, 0, 0, width, height)
        val ySize = width * height
        val out = ByteArray(ySize + ySize / 2)
        var yIndex = 0
        var uvIndex = ySize
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uvIndex++] = u.coerceIn(0, 255).toByte()
                    out[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }
}
