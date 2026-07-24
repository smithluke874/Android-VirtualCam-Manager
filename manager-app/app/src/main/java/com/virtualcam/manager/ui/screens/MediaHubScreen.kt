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
import androidx.compose.ui.\u004dodifier as ComposeModifier
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

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { onPicked(it) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { onPicked(it) }
    val pickAny = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { onPicked(it) }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Media Hub") }) }) { padding ->
        Column(
            modifier = ComposeModifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Upload spoof media", style = MaterialTheme.typography.titleLarge)
            Card(modifier = ComposeModifier.fillMaxWidth()) {
                Column(modifier = ComposeModifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasVideo) Icons.Default.VideoFile else Icons.Default.Upload,
                            contentDescription = null,
                            tint = if (hasVideo) Success else Primary
                        )
                        Spacer(modifier = ComposeModifier.width(12.dp))
                        Column {
                            Text("virtual.mp4", style = MaterialTheme.typography.titleMedium)
                            Text(if (hasVideo) "Ready" else "Not installed", color = if (hasVideo) Success else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (importing || isLoading) {
                        CircularProgressIndicator(modifier = ComposeModifier.height(36.dp).width(36.dp), color = Primary)
                    }
                    Button(
                        onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        enabled = !importing,
                        modifier = ComposeModifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VideoFile, null)
                        Spacer(modifier = ComposeModifier.width(8.dp))
                        Text("Pick video")
                    }
                    Button(
                        onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !importing,
                        modifier = ComposeModifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, null)
                        Spacer(modifier = ComposeModifier.width(8.dp))
                        Text("Pick image (→ looping MP4)")
                    }
                    OutlinedButton(onClick = { pickAny.launch("*/*") }, enabled = !importing, modifier = ComposeModifier.fillMaxWidth()) {
                        Text("Pick any file")
                    }
                    if (hasVideo) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    repo.removeVirtualVideo()
                                    message = "removed"
                                    messageOk = true
                                    refresh()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                        ) { Text("Remove virtual.mp4") }
                    }
                }
            }
            message?.let {
                Text(it, color = if (messageOk) Success else Danger)
            }
            if (listing.isNotEmpty()) {
                Text("Camera1 contents")
                listing.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
