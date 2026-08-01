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
                    Text("What works now (v2.0.0-dev)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "• Magisk module + Zygisk native library (multi-ABI, 16 KB pages)\n" +
                                "• This APK manages virtual.mp4 + enable flag (3-step UX)\n" +
                                "• No LSPosed / Xposed / second framework\n" +
                                "• Native OpenGL interception (ShadowHook on glBindTexture / glDraw*)\n" +
                                "• AMediaCodec continuous decode → RGB texture upload\n" +
                                "• Status written to /data/adb/virtualcam/hook_status",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Honest limitation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "The current GL path uploads RGB into a GL_TEXTURE_2D and redirects " +
                                "EXTERNAL_OES binds. Many Camera2 / CameraX shaders use samplerExternalOES " +
                                "and may ignore a 2D texture. Phase 2.1 (OES SurfaceTexture / EGLImage) " +
                                "is the next step for broader app coverage.\n\n" +
                                "Do not claim a full camera spoof until real apps show the virtual feed on device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Install (3 steps)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "1. Magisk → Zygisk ON → flash the module zip → reboot\n" +
                                "2. Install this Manager APK → grant root\n" +
                                "3. Home → Pick video/image → flip VirtualCam ON → open any camera app",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Control plane files", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "/data/adb/virtualcam/enabled\n" +
                                "/data/adb/virtualcam/hook_status\n" +
                                "/data/adb/virtualcam/virtual.mp4  (fallback)\n" +
                                "/storage/emulated/0/DCIM/Camera1/virtual.mp4  (primary)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
