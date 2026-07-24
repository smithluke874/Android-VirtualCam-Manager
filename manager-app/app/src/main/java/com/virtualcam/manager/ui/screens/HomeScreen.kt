package com.virtualcam.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.virtualcam.manager.data.AutoSetup
import com.virtualcam.manager.data.PrerequisiteChecker
import com.virtualcam.manager.data.PrerequisiteStatus
import com.virtualcam.manager.ui.theme.Primary
import com.virtualcam.manager.ui.theme.Success
import com.virtualcam.manager.ui.theme.Danger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val checker = remember { PrerequisiteChecker() }
    val autoSetup = remember { AutoSetup() }
    var status by remember { mutableStateOf<PrerequisiteStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(false) }
    var setupMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            isLoading = true
            status = checker.check()
            enabled = autoSetup.isEnabled()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        status = checker.check()
        if (status?.rootAvailable == true) {
            autoSetup.runFullSetup(enable = autoSetup.isEnabled())
        }
        enabled = autoSetup.isEnabled()
        status = checker.check()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VirtualCam Manager") },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("One-tap control", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Pure Magisk + single APK — pick media in Media tab, toggle ON here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) Success.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (enabled) "VirtualCam is ON" else "VirtualCam is OFF",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (enabled) "enabled=1 · disable.jpg cleared"
                                else "Tap to prepare paths + activate",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = enabled,
                            enabled = !busy && status?.rootAvailable == true,
                            onCheckedChange = { want ->
                                scope.launch {
                                    busy = true
                                    val r = autoSetup.runFullSetup(enable = want)
                                    setupMessage = r.message
                                    enabled = autoSetup.isEnabled()
                                    status = checker.check()
                                    busy = false
                                }
                            }
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                val r = autoSetup.runFullSetup(enable = true)
                                setupMessage = r.message
                                enabled = true
                                status = checker.check()
                                busy = false
                            }
                        },
                        enabled = !busy && status?.rootAvailable == true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "Working…" else "Run full auto-setup")
                    }
                }
            }

            setupMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Success)
            }

            Text("System status", style = MaterialTheme.typography.titleMedium)

            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                status?.let { s ->
                    StatusCard("Root Access", s.rootAvailable)
                    StatusCard("Magisk", s.magiskPresent)
                    StatusCard(
                        title = if (s.moduleVersion != null) "Module (${s.moduleVersion})" else "VirtualCam Module",
                        ok = s.moduleInstalled
                    )
                    StatusCard("Zygisk .so installed", s.zygiskLibPresent)
                    StatusCard("Camera1 Directory", s.camera1Ready)
                    StatusCard("virtual.mp4 present", s.hasVirtualVideo)
                    StatusCard("Control plane enabled", s.vcamEnabled)

                    if (s.hookStatus != null) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Zygisk hook status", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "status: ${s.hookStatus}" +
                                            (s.lastHookPkg?.let { "\nlast pkg: $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (!s.moduleInstalled) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.12f))
                        ) {
                            Text(
                                text = "Flash Magisk module from Actions artifact, then reboot.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Text("Flow", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "1. Magisk → Zygisk ON → flash module → reboot\n" +
                        "2. Install APK → grant root → toggle ON\n" +
                        "3. Media tab → Pick video or image (auto-installs virtual.mp4)\n" +
                        "4. Open camera app → frames inject from your media\n" +
                        "5. Images become a short looping MP4 automatically",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, ok: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Icon(
                imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (ok) Success else Danger
            )
        }
    }
}
