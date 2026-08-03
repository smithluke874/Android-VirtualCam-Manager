package com.virtualcam.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.virtualcam.manager.data.PrerequisiteStatus
import com.virtualcam.manager.ui.theme.Danger
import com.virtualcam.manager.ui.theme.Success

@Composable
fun FailureDoctorCard(
    status: PrerequisiteStatus?,
    enabled: Boolean,
    live: Boolean,
    partial: Boolean,
    hook: String,
    frames: Int,
    hits: Int,
    rootOk: Boolean,
    busy: Boolean,
    onProbe: () -> Unit,
    onRefresh: () -> Unit,
) {
    if (!enabled && status?.diag == null) return
    val err = status?.lastError.orEmpty()
    val title = when {
        live && status?.pathMode == "oes" -> "Doctor: OES path healthy"
        live -> "Doctor: decoder alive path=${status?.pathMode ?: "2d"}"
        err == "shadowhook_missing" || hook.contains("pending_shadowhook") -> "Doctor: ShadowHook missing"
        err == "no_video" || hook == "no_video" -> "Doctor: no virtual video"
        err == "oes_ext_missing" || hook.contains("oes_fallback") -> "Doctor: OES missing - using 2D"
        partial -> "Doctor: starting / waiting"
        else -> "Doctor: idle"
    }
    val body = when {
        live && status?.pathMode == "oes" ->
            "AHardwareBuffer+EGLImage active. Frames=$frames Binds=$hits. Verify feed in a real camera app."
        live ->
            "Native path running. Some apps need OES or SurfaceTexture (next). Probe for details."
        err == "shadowhook_missing" || hook.contains("pending_shadowhook") ->
            "Re-flash Magisk zip with ShadowHook libs, reboot."
        err == "no_video" || hook == "no_video" ->
            "Pick a video/image or place virtual.mp4 in Camera1 or /data/adb/virtualcam."
        err == "oes_ext_missing" || hook.contains("oes_fallback") ->
            "Device lacks EGL Image / OES. 2D active; SurfaceTexture path is next."
        partial ->
            "Status: $hook. Open a camera app so Zygisk loads, then Run Probe."
        else ->
            "Turn ON, pick video, open camera app, then Run Probe."
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (live) Success.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            status?.diag?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            status?.lastError?.takeIf { it.isNotBlank() }?.let {
                Text("last_error: $it", style = MaterialTheme.typography.labelSmall, color = Danger)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onProbe,
                    enabled = !busy && rootOk,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Science, contentDescription = "Probe")
                    Spacer(Modifier.width(6.dp))
                    Text("Run Probe")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
            }
        }
    }
}
