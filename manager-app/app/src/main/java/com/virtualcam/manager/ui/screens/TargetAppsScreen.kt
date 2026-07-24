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
            TopAppBar(title = { Text("Architecture") })
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
                text = "Pure Magisk + Single APK",
                style = MaterialTheme.typography.titleLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What works now", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "• Magisk module creates /DCIM/Camera1 at boot\n" +
                                "• This APK manages virtual.mp4 + all classic flag files\n" +
                                "• No LSPosed / Xposed / second framework required\n" +
                                "• Paths match original android_virtual_cam exactly",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What original VCAM did", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Original VCAM was an Xposed module. It hooked:\n" +
                                "• Camera1: PreviewCallback, PictureCallback, SurfaceTexture\n" +
                                "• Camera2: CameraManager, CameraDevice, CaptureRequest\n" +
                                "Then fed frames from virtual.mp4 into the app.\n\n" +
                                "The path/flag files alone do not replace the camera feed — " +
                                "they are the control plane the hook reads.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next: Zygisk native hooks", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "To stay pure Magisk (no LSPosed), frame injection will be " +
                                "implemented as Zygisk .so libraries inside this same Magisk module.\n\n" +
                                "The zygisk/ folder is already prepared. Once the native hooks land, " +
                                "you still only flash this one module + install this one APK.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Until Zygisk hooks are complete, use this stack to prepare the " +
                        "environment and verify paths/flags on your device. Any future " +
                        "hook (or app that already respects the classic layout) will work " +
                        "with the same files this APK manages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
