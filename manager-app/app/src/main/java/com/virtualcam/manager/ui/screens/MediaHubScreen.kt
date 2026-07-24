package com.virtualcam.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.virtualcam.manager.data.FileSystemRepository
import com.virtualcam.manager.data.MediaImportHelper
import com.virtualcam.manager.ui.theme.Danger
import com.virtualcam.manager.ui.theme.Primary
import com.virtualcam.manager.ui.theme.Success
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaHubScreen() {
    val context = LocalContext.current
    val repo = remember { FileSystemRepository() }
    val importer = remember { MediaImportHelper(repo) }
    var hasVideo by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
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

    fun onPicked(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            importing = true
            message = "Processing…"
            messageOk = true
            val result = importer.importFromUri(context, uri)
            message = result.message
            messageOk = result.success
            importing = false
            refresh()
        }
    }

    // Modern Photo Picker (no storage permission required on API 33+)
    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPicked(uri) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPicked(uri) }

    // Fallback GetContent for older devices / any file
    val pickAny = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> onPicked(uri) }

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
            Text("Upload spoof media", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Pick a video or image. The app installs it as virtual.mp4, enables VirtualCam, and Zygisk injects it into camera apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasVideo) Icons.Default.VideoFile else Icons.Default.Upload,
                            contentDescription = null,
                            tint = if (hasVideo) Success else Primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("virtual.mp4", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (hasVideo) "Ready in /DCIM/Camera1/" else "Not installed yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasVideo) Success else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (importing || isLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    Button(
                        onClick = {
                            pickVideo.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick video")
                    }

                    Button(
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick image (→ looping MP4)")
                    }

                    OutlinedButton(
                        onClick = { pickAny.launch("*/*") },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pick any file (fallback)")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val src = repo.importVirtualFromDownloads()
                                    message = if (src != null) {
                                        messageOk = true
                                        "Imported from Downloads:\n$src"
                                    } else {
                                        messageOk = false
                                        "No virtual.mp4 found in Download/DCIM/Movies"
                                    }
                                    refresh()
                                }
                            },
                            enabled = !importing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("From Downloads")
                        }
                        OutlinedButton(
                            onClick = { refresh() },
                            enabled = !importing,
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

            message?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (messageOk) Success.copy(alpha = 0.15f)
                        else Danger.copy(alpha = 0.12f)
                    )
                ) {
                    Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (listing.isNotEmpty()) {
                Text("Camera1 contents", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        listing.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text("How injection works", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "1. Pick image or video here → installed as virtual.mp4\n" +
                        "2. App sets enabled=1 and clears disable.jpg\n" +
                        "3. Flash Magisk module (Zygisk on) if not already\n" +
                        "4. Open any camera app — Zygisk loads and injects frames\n" +
                        "5. Images are encoded to a short looping MP4 automatically\n\n" +
                        "Tip: match resolution to the target app preview when possible.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
