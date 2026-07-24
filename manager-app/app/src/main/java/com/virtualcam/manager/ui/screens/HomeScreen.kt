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
    var status by remember { mutableStateOf<PrerequisiteStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            isLoading = true
            status = checker.check()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

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
            Text(
                text = "System Status",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Pure Magisk + single APK mode (no LSPosed)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
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
                    StatusCard("Camera1 Directory", s.camera1Ready)
                    StatusCard("Module Control Dir", s.moduleControlDir)

                    Spacer(modifier = Modifier.height(8.dp))

                    if (s.allPassed) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = if (s.moduleInstalled) {
                                    "Environment ready. Module detected. Use Media + Settings tabs to place virtual.mp4 and control flags."
                                } else {
                                    "Root + Magisk OK, but Magisk module not detected. Flash VirtualCam-Manager-Magisk zip, then reboot."
                                },
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Missing requirements",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "This solution needs only Magisk + root.\n1. Flash the Magisk module\n2. Reboot\n3. Grant this APK root access",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("How it works", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "• Magisk module prepares /DCIM/Camera1 exactly as the original VCAM exploit expected\n" +
                        "• This single APK is the only control surface\n" +
                        "• Place virtual.mp4 and toggle the classic flag files from Settings\n" +
                        "• No LSPosed / Xposed required\n" +
                        "• Zygisk native frame injection is the next major step",
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
