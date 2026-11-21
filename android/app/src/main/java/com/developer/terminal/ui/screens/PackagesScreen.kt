package com.developer.terminal.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PackagesScreen(app: KestrelApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = app.packageEngine

    val installed by engine.observeInstalled().collectAsState(initial = emptyList())
    val repos by engine.observeRepos().collectAsState(initial = emptyList())
    val logs by engine.observeLogs().collectAsState(initial = emptyList())
    val catalog by engine.catalog.collectAsState()
    val downloadState by engine.downloadState.collectAsState()
    val syncing by engine.syncing.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var statusNotice by remember { mutableStateOf("") }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var repoName by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("https://dl-cdn.alpinelinux.org/alpine/v3.20/community/aarch64") }

    val installedNames = remember(installed) { installed.map { it.name }.toSet() }
    val filteredCatalog = remember(catalog, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val list = if (q.isEmpty()) catalog else catalog.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
        list.take(150)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Toolbar
        Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Package Manager (aarch64)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${installed.size} installed · ${catalog.size} indexed in APKINDEX",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            enabled = !syncing && !downloadState.active,
                            onClick = {
                                scope.launch {
                                    val res = engine.syncRepositories()
                                    statusNotice = res.fold(
                                        onSuccess = { "Synced $it aarch64 packages" },
                                        onFailure = { "Sync failed: ${it.message}" }
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(if (syncing) "Syncing..." else "Update")
                        }
                        Button(
                            enabled = !syncing && !downloadState.active,
                            onClick = {
                                scope.launch {
                                    val res = engine.upgradeAll()
                                    statusNotice = res.fold(
                                        onSuccess = { "Upgraded $it package(s)" },
                                        onFailure = { "Upgrade error: ${it.message}" }
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Upgrade")
                        }
                    }
                }

                if (downloadState.active) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${downloadState.packageName}: ${downloadState.stage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }
                } else if (statusNotice.isNotBlank()) {
                    Text(statusNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Alpine aarch64 packages (python3, nodejs, git, clang, rust...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Catalog (${catalog.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Installed (${installed.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Repos & Logs") })
        }

        when (selectedTab) {
            0 -> {
                if (catalog.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Repository index not downloaded yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap Update to fetch APKINDEX.tar.gz from Alpine Linux v3.20 aarch64 mirrors.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { scope.launch { engine.syncRepositories() } }) {
                            Text("Sync Repositories Now")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCatalog, key = { it.name }) { pkg ->
                            val isInstalled = pkg.name in installedNames
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${pkg.name} ${pkg.version}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                        Text(pkg.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "${pkg.architecture} · ${Formatter.formatShortFileSize(context, pkg.sizeBytes)} · Deps: ${pkg.dependencies.take(4).joinToString().ifEmpty { "none" }}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isInstalled) {
                                        Text("INSTALLED", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        IconButton(
                                            enabled = !downloadState.active,
                                            onClick = {
                                                scope.launch {
                                                    val r = engine.installPackage(pkg)
                                                    statusNotice = r.fold(
                                                        onSuccess = { "Installed ${pkg.name}" },
                                                        onFailure = { "Install failed: ${it.message}" }
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = "Install ${pkg.name}", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                val filteredInstalled = installed.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredInstalled, key = { it.name }) { pkg ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${pkg.name} (${pkg.version})", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                    Text(pkg.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${pkg.repository} · ${pkg.installPath}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!pkg.isNativeExecutable) {
                                    IconButton(onClick = { scope.launch { engine.removePackage(pkg) } }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Package Repositories", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = { showAddRepoDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Add Repo")
                            }
                        }
                    }
                    items(repos, key = { it.id }) { repo ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(repo.name, fontWeight = FontWeight.Medium)
                                Text(repo.baseUrl, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Installation & Sync Logs", style = MaterialTheme.typography.titleMedium)
                    }
                    items(logs, key = { it.id }) { log ->
                        val ts = remember(log.timestamp) {
                            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp))
                        }
                        Text(
                            "[$ts] ${log.action} ${log.packageName} -> ${log.status}: ${log.message}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (log.status == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add Alpine aarch64 Repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = repoName, onValueChange = { repoName = it }, label = { Text("Repository Name") }, singleLine = true)
                    OutlinedTextField(value = repoUrl, onValueChange = { repoUrl = it }, label = { Text("Base Mirror URL") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (repoName.isNotBlank() && repoUrl.isNotBlank()) {
                        scope.launch { engine.addRepository(repoName, repoUrl) }
                        showAddRepoDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRepoDialog = false }) { Text("Cancel") }
            }
        )
    }
}
