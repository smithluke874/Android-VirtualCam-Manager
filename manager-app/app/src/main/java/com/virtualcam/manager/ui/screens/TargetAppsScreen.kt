package com.virtualcam.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetAppsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Info") })
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
                text = "No LSPosed Scope Needed",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Because this is a pure Magisk + single APK solution, there is no Xposed module to enable per-app.\n\n" +
                        "The Magisk module prepares the global Camera1 environment that the original VCAM exploit used.\n\n" +
                        "Place your video as virtual.mp4 via the Media tab and control behaviour with the flag toggles in Settings.\n\n" +
                        "Future Zygisk native hooks (inside the Magisk module) will provide the actual camera frame replacement without needing any second framework.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
