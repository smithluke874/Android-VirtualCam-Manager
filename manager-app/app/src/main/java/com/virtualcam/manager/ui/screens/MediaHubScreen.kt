package com.virtualcam.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.virtualcam.manager.data.FileSystemRepository
import com.virtualcam.manager.ui.theme.Primary
import com.virtualcam.manager.ui.theme.Success
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaHubScreen() {
    val repo = remember { FileSystemRepository() }
    var hasVideo by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            isLoading = true
            hasVideo = repo.hasVirtualVideo()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Media Hub") })
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
                text = "Virtual Video",
                style = MaterialTheme.typography.titleLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, tint = Primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("virtual.mp4", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (hasVideo) "Present in /DCIM/Camera1/" else "Not found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasVideo) Success else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Place a video named exactly virtual.mp4 into the Camera1 folder. Resolution should match the target app's preview.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    repo.ensureGlobalCamera1()
                                    message = "Camera1 directory ready. Copy virtual.mp4 there via a file manager."
                                    refresh()
                                }
                            }
                        ) {
                            Text("Prepare Directory")
                        }
                        OutlinedButton(onClick = { refresh() }) {
                            Text("Refresh")
                        }
                    }
                }
            }

            message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Success)
            }

            Text(
                text = "Tips",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "• Front camera often needs horizontal flip + 90° rotation\n" +
                        "• Create no-silent.jpg if you want audio from the video\n" +
                        "• Create private_dir.jpg to force per-app private path\n" +
                        "• Future version will include on-device FFmpeg transcoding",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
