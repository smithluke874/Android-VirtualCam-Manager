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

    // Live status poll while enabled — keeps Home feeling alive without extra taps
    LaunchedEffect(enabled) {
        while (enabled) {
            delay(2000)
            status = checker.check()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            msg = "Importing…"
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
            val live = enabled && (hook.startsWith("nv21_video") ||
                    hook.startsWith("nv21_pattern") ||
                    hook.startsWith("surface_playing") ||
                    hook.startsWith("texture_swapped"))

            val (title, body, ok) = when {
                !rootOk -> Triple("Grant root", "Allow root in Magisk for this app, then refresh.", false)
                !modOk -> Triple("Flash Magisk module", "Install the Magisk zip, enable Zygisk, reboot.", false)
                !vidOk -> Triple("Pick a video or image", "One tap below. Images become a looping video.", false)
                !enabled -> Triple("Turn VirtualCam ON", "Flip the switch. Then open any camera app.", false)
                live -> Triple("Working", "Camera apps are seeing your video.", true)
                else -> Triple("Ready", "Open any camera app. Status updates automatically.", true)
            }

            // Primary guidance card
            Card(
                M.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (ok) Success.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(M.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    if (enabled && hook.isNotEmpty()) {
                        Text(
                            "Status: $hook${s?.lastHookPkg?.let { \" · $it\" } ?: \"\"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (live) Success else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Big ON/OFF row
            Card(M.fillMaxWidth()) {
                Row(
                    M.fillMaxWidth().padding(16.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (enabled) "VirtualCam is ON" else "VirtualCam is OFF",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (enabled) "Camera apps see your video" else "Tap to turn on",
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

            // One-tap media pick
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
                ).forEach { (title, good) ->
                    Card(M.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            M.fillMaxWidth().padding(12.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text(title)
                            Icon(
                                if (good) Icons.Default.CheckCircle else Icons.Default.Error,
                                null,
                                tint = if (good) Success else Danger
                            )
                        }
                    }
                }
                s.hookStatus?.let {
                    Text(
                        "Hook: $it${s.lastHookPkg?.let { p -> \" · $p\" } ?: \"\"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
