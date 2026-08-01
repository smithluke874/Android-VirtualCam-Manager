package com.virtualcam.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier as M
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.virtualcam.manager.data.AutoSetup
import com.virtualcam.manager.data.MediaImportHelper
import com.virtualcam.manager.data.PrerequisiteChecker
import com.virtualcam.manager.ui.theme.Danger
import com.virtualcam.manager.ui.theme.Primary
import com.virtualcam.manager.ui.theme.Success
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val checker = remember { PrerequisiteChecker() }
    val setup = remember { AutoSetup() }
    val importer = remember { MediaImportHelper() }
    var status by remember { mutableStateOf(checker.let { null as com.virtualcam.manager.data.PrerequisiteStatus? }) }
    var loading by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var msgOk by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() = scope.launch {
        loading = true
        status = checker.check()
        enabled = setup.isEnabled()
        loading = false
    }

    LaunchedEffect(Unit) {
        status = checker.check()
        if (status?.rootAvailable == true) setup.runFullSetup(enable = setup.isEnabled())
        enabled = setup.isEnabled()
        status = checker.check()
        loading = false
    }

    // Live status poll while enabled — keeps Home feeling alive
    LaunchedEffect(enabled) {
        while (enabled) {
            delay(1200)
            status = checker.check()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            msg = "Importing..."
            msgOk = true
            val r = importer.importFromUri(ctx, uri)
            msg = r.message
            msgOk = r.success
            if (r.success && status?.rootAvailable == true) {
                setup.runFullSetup(enable = true)
                enabled = true
                msg = "Ready — open any camera app"
                msgOk = true
            }
            status = checker.check()
            busy = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("VirtualCam") },
            actions = {
                IconButton(onClick = { refresh() }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        )
    }) { pad ->
        Column(
            M.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (loading) {
                Box(M.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
                return@Column
            }

            val s = status
            val rootOk = s?.rootAvailable == true
            val modOk = s?.moduleInstalled == true || s?.zygiskLibPresent == true
            val vidOk = s?.hasVirtualVideo == true
            val hook = s?.hookStatus.orEmpty()
            val frames = s?.decoderFrames ?: 0
            val texId = s?.textureId ?: 0L
            val hits = s?.bindHits ?: 0

            val live = enabled && (
                hook.startsWith("gl_bind_redir") ||
                hook.startsWith("decoder_frames") ||
                hook.startsWith("gl_ready") ||
                hook.startsWith("gl_hooked") ||
                hook.startsWith("decoder_running") ||
                hook.startsWith("gl_tex_created") ||
                frames > 0 ||
                hits > 0 ||
                hook.startsWith("nv21_video") ||
                hook.startsWith("nv21_pattern") ||
                hook.startsWith("surface_playing") ||
                hook.startsWith("texture_swapped")
            )
            val partial = enabled && !live && (
                hook.startsWith("gl_partial") ||
                hook.startsWith("gl_hook_fail") ||
                hook.startsWith("gl_hook_pending") ||
                hook.startsWith("gl_installing") ||
                hook.startsWith("decoder_start") ||
                hook.startsWith("no_video") ||
                hook.isNotEmpty()
            )

            val (title, body, ok) = when {
                !rootOk -> Triple("Grant root", "Allow root in Magisk for this app, then refresh.", false)
                !modOk -> Triple("Flash Magisk module", "Install the Magisk zip, enable Zygisk, reboot.", false)
                !vidOk -> Triple("Pick a video or image", "One tap below. Images become a looping video.", false)
                !enabled -> Triple("Turn VirtualCam ON", "Flip the switch. Then open any camera app.", false)
                live -> Triple("Active", "Hooks + decoder running. Open a camera app to test the feed.", true)
                partial -> Triple("Starting…", "Native path is installing or waiting for frames. Status updates automatically.", true)
                else -> Triple("Ready", "Open any camera app. Status updates automatically.", true)
            }

            Card(
                M.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        live -> Success.copy(alpha = 0.14f)
                        ok -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(M.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    if (enabled && hook.isNotEmpty()) {
                        val pkgSuffix = s?.lastHookPkg?.let { pkg -> " · $pkg" } ?: ""
                        Text(
                            "Status: $hook$pkgSuffix",
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                live -> Success
                                partial -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (enabled && (frames > 0 || texId > 0 || hits > 0 || live || partial)) {
                Row(
                    M.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Frames $frames") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (frames > 0) Success.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("Tex ${if (texId > 0) texId else "—"}") }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("Binds $hits") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (hits > 0) Success.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Card(M.fillMaxWidth()) {
                Row(
                    M.fillMaxWidth().padding(16.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column(M.weight(1f)) {
                        Text(
                            if (enabled) "VirtualCam is ON" else "VirtualCam is OFF",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (enabled) "Native GL path active — open a camera app"
                            else "Tap to turn on",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = !busy && rootOk,
                        onCheckedChange = { want ->
                            scope.launch {
                                busy = true
                                val r = setup.runFullSetup(enable = want)
                                msg = r.message
                                msgOk = r.rootOk
                                enabled = setup.isEnabled()
                                status = checker.check()
                                busy = false
                            }
                        }
                    )
                }
            }

            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                enabled = !busy && rootOk,
                modifier = M.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, null)
                Spacer(M.width(8.dp))
                Text(
                    when {
                        busy -> "Working…"
                        vidOk -> "Replace video / image"
                        else -> "Pick video or image"
                    }
                )
            }
            if (!vidOk) {
                Text(
                    "Images become a short looping video automatically.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            msg?.let { Text(it, color = if (msgOk) Success else Danger) }

            if (enabled) {
                Card(
                    M.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(M.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "What to expect right now",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "v2 intercepts OpenGL texture binds and uploads decoded frames. " +
                                    "Many camera apps still use samplerExternalOES — if the preview " +
                                    "stays black or real, that is expected until Phase 2.1 (OES SurfaceTexture). " +
                                    "Watch Frames / Binds rise when you open a camera app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TextButton(onClick = { details = !details }, modifier = M.fillMaxWidth()) {
                Text(if (details) "Hide details" else "Show details")
            }

            if (details && s != null) {
                listOf(
                    "Root" to s.rootAvailable,
                    "Magisk" to s.magiskPresent,
                    (if (s.moduleVersion != null) "Module (${s.moduleVersion})" else "Module") to
                            (s.moduleInstalled || s.zygiskLibPresent),
                    "Zygisk .so" to s.zygiskLibPresent,
                    "virtual.mp4" to s.hasVirtualVideo,
                    "Enabled" to s.vcamEnabled
                ).forEach { (label, good) ->
                    Card(M.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            M.fillMaxWidth().padding(12.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text(label)
                            Icon(
                                if (good) Icons.Default.CheckCircle else Icons.Default.Error,
                                null,
                                tint = if (good) Success else Danger
                            )
                        }
                    }
                }
                s.hookStatus?.let { hs ->
                    val pkgSuffix = s.lastHookPkg?.let { pkg -> " · $pkg" } ?: ""
                    Text(
                        "Hook: $hs$pkgSuffix",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (frames > 0 || texId > 0 || hits > 0) {
                    Text(
                        "Telemetry: frames=$frames  texture=$texId  binds=$hits",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "v2 uses native OpenGL interception (ShadowHook + MediaCodec). " +
                            "Device verification is required before claiming a full camera spoof.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
