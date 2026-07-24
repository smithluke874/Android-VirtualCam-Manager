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
import com.virtualcam.manager.ui.theme.Danger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaHubScreen() {
    val repo = remember { FileSystemRepository() }
    var hasVideo by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(true) }
    var listing by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            isLoading = true
            hasVideo = repo.hasVirtualVideo()
            listing = repo.listCamera1Contents()
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("virtual.mp4", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (hasVideo) "Present in /DCIM/Camera1/" else "Not found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasVideo) Success else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Target path:\n/storage/emulated/0/DCIM/Camera1/virtual.mp4\n\n" +
                                "Easiest test flow:\n" +
                                "1. Copy any .mp4 to Download and rename it virtual.mp4\n" +
                                "2. Tap Import from Downloads below",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val src = repo.importVirtualFromDownloads()
                                    if (src != null) {
                                        message = "Imported from:\n$src"
                                        messageOk = true
                                    } else {
                                        message = "No virtual.mp4 found in Download / DCIM / Movies.\nRename a video to virtual.mp4 and put it in Download, then try again."
                                        messageOk = false
                                    }
                                    refresh()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import from Downloads")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repo.ensureGlobalCamera1()
                                        message = "Camera1 directory ready."
                                        messageOk = true
                                        refresh()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Prepare Dir")
                            }
                            OutlinedButton(
                                onClick = { refresh() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Refresh")
                            }
                        }
                        if (hasVideo) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        repo.removeVirtualVideo()
                                        message = "virtual.mp4 removed"
                                        messageOk = true
                                        refresh()
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                            ) {
                                Text("Remove virtual.mp4")
                            }
                        }
                    }
                }
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (messageOk) Success else Danger
                )
            }

            if (listing.isNotEmpty()) {
                Text("Camera1 contents", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        listing.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text("Tips", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "• Front camera often needs horizontal flip + 90° rotation\n" +
                        "• Create no-silent.jpg if you want audio from the video\n" +
                        "• Create private_dir.jpg to force per-app private path\n" +
                        "• Resolution should match the target app preview when possible",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
