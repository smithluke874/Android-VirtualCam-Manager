package com.virtualcam.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier as ComposeModifier
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

    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPicked(uri) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPicked(uri) }

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
            modifier = ComposeModifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Upload spoof media", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Pick a video or image. The app installs it as virtual.mp4 and enables VirtualCam.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = ComposeModifier.fillMaxWidth()) {
                Column(
                    modifier = ComposeModifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasVideo) Icons.Default.VideoFile else Icons.Default.Upload,
                            contentDescription = null,
                            tint = if (hasVideo) Success else Primary
                        )
                        Spacer(modifier = ComposeModifier.width(12.dp))
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
                        Row(
                            modifier = ComposeModifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = ComposeModifier.height(36.dp).width(36.dp),
                                color = Primary
                            )
                        }
                    }

                    Button(
                        onClick = {
                            pickVideo.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                        enabled = !importing,
                        modifier = ComposeModifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null)
                        Spacer(modifier = ComposeModifier.width(8.dp))
                        Text("Pick video")
                    }

                    Button(
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        enabled = !importing,
                        modifier = ComposeModifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = ComposeModifier.width(8.dp))
                        Text("Pick image (→ looping MP4)")
                    }

                    OutlinedButton(
                        onClick = { pickAny.launch("*/*") },
                        enabled = !importing,
                        modifier = ComposeModifier.fillMaxWidth()
                    ) {
                        Text("Pick any file (fallback)")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val src = repo.importVirtualFromDownloads()
                                    if (src != null) {
                                        messageOk = true
                                        message = "Imported from Downloads:\n$src"
                                    } else {
                                        messageOk = false
                                        message = "No virtual.mp4 found in Download/DCIM/Movies"
                                    }
                                    refresh()
                                }
                            },
                            enabled = !importing,
                            modifier = ComposeModifier.weight(1f)
                        ) {
                            Text("From Downloads")
                        }
                        OutlinedButton(
                            onClick = { refresh() },
                            enabled = !importing,
                            modifier = ComposeModifier.weight(1f)
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

            message?.let { msg ->
                Card(
                    modifier = ComposeModifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (messageOk) Success.copy(alpha = 0.15f)
                        else Danger.copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        text = msg,
                        modifier = ComposeModifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (listing.isNotEmpty()) {
                Text("Camera1 contents", style = MaterialTheme.typography.titleMedium)
                Card(modifier = ComposeModifier.fillMaxWidth()) {
                    Column(modifier = ComposeModifier.padding(12.dp)) {
                        listing.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
