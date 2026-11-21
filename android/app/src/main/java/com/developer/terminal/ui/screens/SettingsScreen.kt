package com.developer.terminal.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.developer.terminal.KestrelApplication
import com.developer.terminal.data.db.AliasEntity
import com.developer.terminal.data.prefs.AccentPalette
import com.developer.terminal.data.prefs.AppThemeMode
import com.developer.terminal.data.prefs.TerminalColorScheme
import com.developer.terminal.terminal.TerminalForegroundService
import com.developer.terminal.widgets.refreshAllKestrelWidgets
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(app: KestrelApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val aliases by app.database.dao().observeAliases().collectAsState(initial = emptyList())

    var aliasName by remember { mutableStateOf("") }
    var aliasCmd by remember { mutableStateOf("") }
    var startupScript by remember {
        mutableStateOf(runCatching { File(app.terminalManager.homeDir, ".kestrelrc").readText() }.getOrDefault(""))
    }
    var statusNotice by remember { mutableStateOf("") }

    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val tmpZip = File(context.cacheDir, "kestrel-backup.zip")
                app.toolsEngine.exportWorkspaceBackup(tmpZip).onSuccess { zip ->
                    app.fileManager.exportFileToSafUri(zip, uri).onSuccess {
                        statusNotice = "Exported workspace backup ZIP"
                    }
                }
            }
        }
    }

    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val tmpZip = File(context.cacheDir, "kestrel-restore.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmpZip.outputStream().use { input.copyTo(it) }
                }
                app.toolsEngine.restoreWorkspaceBackup(tmpZip).fold(
                    onSuccess = { count -> statusNotice = "Restored $count file(s) to \$HOME" },
                    onFailure = { statusNotice = "Restore failed: ${it.message}" }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings & Workspace Controls", style = MaterialTheme.typography.headlineMedium)

        if (statusNotice.isNotBlank()) {
            Text(statusNotice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        SettingsSection("Appearance & Accent") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    if (settings.themeMode == mode) Button(onClick = {}) { Text(mode.name) }
                    else OutlinedButton(onClick = { app.settings.update { it.copy(themeMode = mode) } }) { Text(mode.name) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                AccentPalette.entries.forEach { acc ->
                    if (settings.accent == acc) Button(onClick = {}) { Text(acc.label) }
                    else OutlinedButton(onClick = { app.settings.update { it.copy(accent = acc) } }) { Text(acc.label) }
                }
            }
        }

        SettingsSection("Terminal Theme & Font") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TerminalColorScheme.entries.forEach { sch ->
                    if (settings.terminalScheme == sch) Button(onClick = {}) { Text(sch.label) }
                    else OutlinedButton(onClick = { app.settings.update { it.copy(terminalScheme = sch) } }) { Text(sch.label) }
                }
            }
            Text("Font Size: ${settings.terminalFontSizeSp} sp", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Slider(
                value = settings.terminalFontSizeSp.toFloat(),
                onValueChange = { v -> app.settings.update { it.copy(terminalFontSizeSp = v.toInt()) } },
                valueRange = 10f..22f
            )
        }

        SettingsSection("Runtime & Privileges") {
            SettingToggleRow(
                title = "Background Foreground Service",
                subtitle = "Keep PTY shells alive with persistent notification",
                checked = settings.backgroundServiceEnabled,
                onToggle = { enabled ->
                    app.settings.update { it.copy(backgroundServiceEnabled = enabled) }
                    if (enabled) TerminalForegroundService.ensureRunning(context) else TerminalForegroundService.stop(context)
                }
            )
            SettingToggleRow(
                title = "Keep Screen Awake",
                subtitle = "Hold screen wake flag while app is foregrounded",
                checked = settings.keepScreenAwake,
                onToggle = { awake -> app.settings.update { it.copy(keepScreenAwake = awake) } }
            )
            SettingToggleRow(
                title = "Optional Root Binary Inspection",
                subtitle = "Explicit opt-in check for su binary (never assumes root)",
                checked = settings.rootIntegrationOptIn,
                onToggle = { optIn -> app.settings.update { it.copy(rootIntegrationOptIn = optIn) } }
            )
        }

        SettingsSection("Command Aliases") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = aliasName, onValueChange = { aliasName = it }, label = { Text("Alias") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = aliasCmd, onValueChange = { aliasCmd = it }, label = { Text("Command") }, singleLine = true, modifier = Modifier.weight(2f))
            }
            Button(onClick = {
                if (aliasName.isNotBlank() && aliasCmd.isNotBlank()) {
                    scope.launch { app.database.dao().upsertAlias(AliasEntity(aliasName.trim(), aliasCmd.trim())) }
                    aliasName = ""; aliasCmd = ""
                }
            }) { Text("Save Alias") }
            aliases.forEach { a ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("alias ${a.name}='${a.command}'", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { scope.launch { app.database.dao().deleteAlias(a.name) } }) { Text("Delete") }
                }
            }
        }

        SettingsSection("Custom Startup Script (~/.kestrelrc)") {
            OutlinedTextField(value = startupScript, onValueChange = { startupScript = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                File(app.terminalManager.homeDir, ".kestrelrc").writeText(startupScript)
                statusNotice = "Saved ~/.kestrelrc"
            }) { Text("Save ~/.kestrelrc") }
        }

        SettingsSection("Backup, Restore & Widgets") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { backupExportLauncher.launch("kestrel-workspace-backup.zip") }) { Text("Export ZIP Backup") }
                OutlinedButton(onClick = { backupRestoreLauncher.launch(arrayOf("application/zip")) }) { Text("Restore Backup") }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    refreshAllKestrelWidgets(context)
                    statusNotice = "Refreshed all 7 Glance widgets"
                }
            }) { Text("Refresh Home-Screen Widgets") }
        }

        SettingsSection("Quick Widget Commands & Optional AI Key") {
            OutlinedTextField(
                value = settings.quickCommand1,
                onValueChange = { v -> app.settings.update { it.copy(quickCommand1 = v) } },
                label = { Text("Quick Command 1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.quickCommand2,
                onValueChange = { v -> app.settings.update { it.copy(quickCommand2 = v) } },
                label = { Text("Quick Command 2") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.aiApiKey,
                onValueChange = { v -> app.settings.update { it.copy(aiApiKey = v) } },
                label = { Text("Optional Gemini API Key (local encrypted prefs)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.aiEndpoint,
                onValueChange = { v -> app.settings.update { it.copy(aiEndpoint = v) } },
                label = { Text("AI API Endpoint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(8.dp))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
