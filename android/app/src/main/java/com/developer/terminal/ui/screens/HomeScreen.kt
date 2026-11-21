package com.developer.terminal.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.developer.terminal.KestrelApplication
import com.developer.terminal.device.DeviceTelemetry
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    app: KestrelApplication,
    onNavigate: (String) -> Unit,
    onRunQuickCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    val sessions by app.terminalManager.sessions.collectAsState()
    val installed by app.packageEngine.observeInstalled().collectAsState(initial = emptyList())
    var telemetry by remember { mutableStateOf<DeviceTelemetry?>(null) }

    LaunchedEffect(settings.rootIntegrationOptIn) {
        while (true) {
            telemetry = app.deviceTelemetry.collect(checkRootExplicitly = settings.rootIntegrationOptIn)
            delay(4000L)
        }
    }

    val t = telemetry
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "KESTREL WORKSPACE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = t?.let { "${it.manufacturer} ${it.model}" } ?: "Collecting Hardware Telemetry...",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = t?.let {
                        "Android ${it.androidVersion} (API ${it.apiLevel}) · ${it.supportedAbis.joinToString(", ")}"
                    } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    telemetry = app.deviceTelemetry.collect(checkRootExplicitly = settings.rootIntegrationOptIn)
                }
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh telemetry")
            }
        }

        // Quick Launch Actions
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Quick Workspace Actions", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onNavigate("terminal") }) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Open Shell (${sessions.size})")
                    }
                    OutlinedButton(onClick = { onNavigate("editor") }) {
                        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Code Editor")
                    }
                    OutlinedButton(onClick = { onNavigate("packages") }) {
                        Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Packages (${installed.size})")
                    }
                    OutlinedButton(onClick = { onNavigate("files") }) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Files")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(settings.quickCommand1, settings.quickCommand2, settings.quickCommand3)
                        .filter { it.isNotBlank() }
                        .forEach { cmd ->
                            OutlinedButton(onClick = { onRunQuickCommand(cmd) }) {
                                Text("$ $cmd", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                }
            }
        }

        if (t != null) {
            val ramUsed = (t.ramTotalBytes - t.ramAvailBytes).coerceAtLeast(0L)
            val ramFrac = if (t.ramTotalBytes > 0) ramUsed.toFloat() / t.ramTotalBytes else 0f
            val storageUsed = (t.internalTotalBytes - t.internalFreeBytes).coerceAtLeast(0L)
            val storageFrac = if (t.internalTotalBytes > 0) storageUsed.toFloat() / t.internalTotalBytes else 0f

            TelemetryCard(
                icon = Icons.Rounded.Memory,
                title = "CPU & Memory",
                primary = "${t.cpuCores} Cores · ${t.cpuModel}",
                secondary = "RAM ${Formatter.formatShortFileSize(context, ramUsed)} / ${Formatter.formatShortFileSize(context, t.ramTotalBytes)}",
                progress = ramFrac,
                extra = "Core clocks (MHz): ${t.cpuFreqMhz.joinToString(" / ") { if (it > 0) it.toString() else "--" }}"
            )

            TelemetryCard(
                icon = Icons.Rounded.Storage,
                title = "Storage & Workspace",
                primary = "${Formatter.formatShortFileSize(context, t.internalFreeBytes)} free of ${Formatter.formatShortFileSize(context, t.internalTotalBytes)}",
                secondary = "Kestrel sandbox workspace: ${Formatter.formatShortFileSize(context, t.workspaceBytes)}",
                progress = storageFrac,
                extra = "Screen: ${t.screenWidthPx}×${t.screenHeightPx} @ ${t.densityDpi} dpi"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Battery", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("${t.batteryPercent}% · ${if (t.batteryCharging) "Charging" else "Discharging"}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Temp: ${t.batteryTempCelsius?.let { String.format("%.1f°C", it) } ?: "N/A"} · ${t.batteryVoltageMv} mV · ${t.batteryHealth}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Network", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(if (t.networkConnected) t.networkTransport else "Offline", fontWeight = FontWeight.SemiBold)
                        Text(
                            "IPv4: ${t.localIpv4}${t.wifiLinkSpeedMbps?.let { " · ${it} Mbps" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Permissions & Root Dashboard
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Android Sandbox & Permissions", style = MaterialTheme.typography.titleMedium)
                    }
                    t.permissions.forEach { perm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(perm.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(perm.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = if (perm.granted) "GRANTED" else "DENIED",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (perm.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Root Status: ${t.rootDetail}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    icon: ImageVector,
    title: String,
    primary: String,
    secondary: String,
    progress: Float,
    extra: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(extra, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
