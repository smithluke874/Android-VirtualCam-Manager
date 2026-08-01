package com.virtualcam.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.virtualcam.manager.data.FileSystemRepository
import com.virtualcam.manager.ui.theme.Primary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val repo = remember { FileSystemRepository() }
    var disable by remember { mutableStateOf(false) }
    var privateDir by remember { mutableStateOf(false) }
    var noSilent by remember { mutableStateOf(false) }
    var noToast by remember { mutableStateOf(false) }
    var forceShow by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun loadFlags() {
        scope.launch {
            isLoading = true
            disable = repo.isFlagEnabled(FileSystemRepository.FLAG_DISABLE)
            privateDir = repo.isFlagEnabled(FileSystemRepository.FLAG_PRIVATE_DIR)
            noSilent = repo.isFlagEnabled(FileSystemRepository.FLAG_NO_SILENT)
            noToast = repo.isFlagEnabled(FileSystemRepository.FLAG_NO_TOAST)
            forceShow = repo.isFlagEnabled(FileSystemRepository.FLAG_FORCE_SHOW)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadFlags() }

    fun toggle(flag: String, current: Boolean, setter: (Boolean) -> Unit) {
        scope.launch {
            val newValue = !current
            if (repo.setFlag(flag, newValue)) {
                setter(newValue)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings & Flags") })
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Runtime Flags",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "These create/delete classic .jpg sentinel files under DCIM/Camera1. " +
                            "The primary ON/OFF switch is the Home screen toggle (writes /data/adb/virtualcam/enabled).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FlagSwitch(
                    title = "Disable Module",
                    subtitle = "Creates disable.jpg – forces gate_off even if enabled flag is set",
                    checked = disable,
                    onCheckedChange = { toggle(FileSystemRepository.FLAG_DISABLE, disable) { disable = it } }
                )
                FlagSwitch(
                    title = "Force Private Directory",
                    subtitle = "Creates private_dir.jpg – use per-app Camera1 path (legacy)",
                    checked = privateDir,
                    onCheckedChange = { toggle(FileSystemRepository.FLAG_PRIVATE_DIR, privateDir) { privateDir = it } }
                )
                FlagSwitch(
                    title = "Enable Video Audio",
                    subtitle = "Creates no-silent.jpg – allow audio from virtual.mp4 (legacy)",
                    checked = noSilent,
                    onCheckedChange = { toggle(FileSystemRepository.FLAG_NO_SILENT, noSilent) { noSilent = it } }
                )
                FlagSwitch(
                    title = "Suppress Toasts",
                    subtitle = "Creates no_toast.jpg – hide toast messages (legacy)",
                    checked = noToast,
                    onCheckedChange = { toggle(FileSystemRepository.FLAG_NO_TOAST, noToast) { noToast = it } }
                )
                FlagSwitch(
                    title = "Force Show",
                    subtitle = "Creates force_show.jpg (legacy)",
                    checked = forceShow,
                    onCheckedChange = { toggle(FileSystemRepository.FLAG_FORCE_SHOW, forceShow) { forceShow = it } }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text("About", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "VirtualCam Manager v2.0.0-dev\n" +
                            "Pure Magisk + Zygisk (NO LSPosed).\n" +
                            "Native OpenGL interception + AMediaCodec.\n" +
                            "Device verification required before claiming a full camera spoof.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun FlagSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange() }
            )
        }
    }
}
