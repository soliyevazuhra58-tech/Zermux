package com.developer.terminal.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.developer.terminal.KestrelApplication
import com.developer.terminal.files.FileNode
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FilesScreen(app: KestrelApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = app.fileManager

    var currentDir by remember { mutableStateOf(repo.homeDir) }
    var nodes by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf("") }
    var clipboardFile by remember { mutableStateOf<Pair<File, Boolean>?>(null) } // file to isMove
    var previewTitle by remember { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf("") }
    var showNewFolder by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameInput by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            if (searchQuery.isNotBlank()) {
                nodes = repo.search(currentDir, searchQuery)
                errorMsg = null
            } else {
                repo.listDirectory(currentDir).fold(
                    onSuccess = { nodes = it; errorMsg = null },
                    onFailure = { errorMsg = it.message ?: "Unable to read directory" }
                )
            }
        }
    }

    LaunchedEffect(currentDir, searchQuery) {
        reload()
    }

    val safImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                repo.importFromSafUri(uri, currentDir).fold(
                    onSuccess = { count -> statusMsg = "Imported $count files into ${currentDir.name}"; reload() },
                    onFailure = { statusMsg = "SAF import failed: ${it.message}" }
                )
            }
        }
    }

    var exportTargetFile by remember { mutableStateOf<File?>(null) }
    val safExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val src = exportTargetFile ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                repo.exportFileToSafUri(src, uri).fold(
                    onSuccess = { statusMsg = "Exported ${src.name}" },
                    onFailure = { statusMsg = "Export failed: ${it.message}" }
                )
            }
        }
    }

    val telemetry = remember(currentDir) { app.deviceTelemetry.collect(false) }
    val storageUsedFrac = if (telemetry.internalTotalBytes > 0) {
        (telemetry.internalTotalBytes - telemetry.internalFreeBytes).toFloat() / telemetry.internalTotalBytes
    } else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { currentDir = repo.homeDir }) { Text("\$HOME") }
                    OutlinedButton(onClick = { currentDir = repo.prefixDir }) { Text("\$PREFIX") }
                    OutlinedButton(onClick = { currentDir = repo.externalRoot }) { Text("/sdcard") }
                    OutlinedButton(onClick = { safImportLauncher.launch(null) }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Import SAF")
                    }
                    OutlinedButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Folder")
                    }
                    clipboardFile?.let { (src, isMove) ->
                        Button(onClick = {
                            scope.launch {
                                val res = if (isMove) repo.move(src, currentDir) else repo.copy(src, currentDir)
                                res.onSuccess {
                                    clipboardFile = null
                                    statusMsg = "${if (isMove) "Moved" else "Copied"} ${src.name}"
                                    reload()
                                }
                            }
                        }) { Text("Paste ${src.name}") }
                    }
                }

                // Storage Bar
                LinearProgressIndicator(progress = { storageUsedFrac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        currentDir.absolutePath,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${Formatter.formatShortFileSize(context, telemetry.internalFreeBytes)} free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { currentDir.parentFile?.let { currentDir = it } }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Up directory")
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Recursive file search...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (statusMsg.isNotBlank()) {
                    Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (errorMsg != null) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Directory Unreadable", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(errorMsg ?: "", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Android Scoped Storage restricts direct listing of /storage/emulated/0 without MANAGE_EXTERNAL_STORAGE. Use SAF Import or \$HOME.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(nodes, key = { it.path }) { node ->
                    val file = File(node.path)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clickable {
                            if (node.isDirectory) {
                                currentDir = file
                                searchQuery = ""
                            } else {
                                scope.launch {
                                    repo.readTextPreview(file).onSuccess { text ->
                                        previewTitle = node.name
                                        previewText = text
                                    }
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (node.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(node.name, fontWeight = FontWeight.Medium)
                                Text(
                                    if (node.isDirectory) "${node.sizeBytes} item(s)" else Formatter.formatShortFileSize(context, node.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!node.isDirectory && file.extension.equals("zip", ignoreCase = true)) {
                                IconButton(onClick = {
                                    scope.launch {
                                        repo.extractZip(file, File(currentDir, file.nameWithoutExtension)).fold(
                                            onSuccess = { n -> statusMsg = "Extracted $n files"; reload() },
                                            onFailure = { statusMsg = "Unzip failed: ${it.message}" }
                                        )
                                    }
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Unarchive, contentDescription = "Extract ZIP", modifier = Modifier.size(18.dp))
                                }
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        val outZip = File(currentDir, "${file.name}.zip")
                                        repo.compressToZip(listOf(file), outZip).fold(
                                            onSuccess = { statusMsg = "Compressed ${outZip.name}"; reload() },
                                            onFailure = { statusMsg = "ZIP failed: ${it.message}" }
                                        )
                                    }
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Archive, contentDescription = "Compress ZIP", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { clipboardFile = file to false; statusMsg = "Copied ${file.name}" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(17.dp))
                            }
                            if (!node.isDirectory) {
                                IconButton(onClick = { exportTargetFile = file; safExportLauncher.launch(file.name) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.FileUpload, contentDescription = "Export SAF", modifier = Modifier.size(17.dp))
                                }
                            }
                            IconButton(onClick = { renameTarget = file; renameInput = file.name }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Rename", modifier = Modifier.size(17.dp))
                            }
                            IconButton(onClick = {
                                scope.launch { repo.delete(file); reload() }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("New Folder") },
            text = { OutlinedTextField(value = folderNameInput, onValueChange = { folderNameInput = it }, label = { Text("Folder name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    if (folderNameInput.isNotBlank()) {
                        scope.launch { repo.createFolder(currentDir, folderNameInput); reload() }
                        folderNameInput = ""
                        showNewFolder = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename ${target.name}") },
            text = { OutlinedTextField(value = renameInput, onValueChange = { renameInput = it }, label = { Text("New name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank()) {
                        scope.launch { repo.rename(target, renameInput); reload() }
                        renameTarget = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    previewTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { previewTitle = null },
            title = { Text(title, fontFamily = FontFamily.Monospace) },
            text = {
                Column(modifier = Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
                    Text(previewText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { previewTitle = null }) { Text("Close") } }
        )
    }
}
