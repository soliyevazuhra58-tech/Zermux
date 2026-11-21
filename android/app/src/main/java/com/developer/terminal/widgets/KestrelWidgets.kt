package com.developer.terminal.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.developer.terminal.MainActivity
import com.developer.terminal.data.prefs.SettingsRepository
import com.developer.terminal.device.DeviceTelemetryRepository
import com.developer.terminal.terminal.TerminalSessionRegistry

private val WidgetBg = Color(0xFF11161D)
private val WidgetSurface = Color(0xFF1A222D)
private val WidgetFg = Color(0xFFE6EDF3)
private val WidgetMuted = Color(0xFF8B949E)
private val WidgetAccent = Color(0xFFE5A00D)

private fun launchIntent(context: Context, route: String, quickCmd: String? = null): Intent {
    return Intent(context, MainActivity::class.java).apply {
        putExtra("route", route)
        if (quickCmd != null) putExtra("quick_command", quickCmd)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

@Composable
private fun WidgetCard(title: String, subtitle: String, body: @Composable () -> Unit) {
    Column(modifier = GlanceModifier.fillMaxSize().background(WidgetBg).padding(12.dp), verticalAlignment = Alignment.Top) {
        Text(text = title, style = TextStyle(color = ColorProvider(WidgetAccent), fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Text(text = subtitle, style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 10.sp))
        Spacer(modifier = GlanceModifier.height(6.dp))
        body()
    }
}

class TerminalLaunchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(WidgetBg).clickable(actionStartActivity(launchIntent(context, "terminal"))).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = ">_ Kestrel", style = TextStyle(color = ColorProvider(WidgetAccent), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(text = "Launch Shell Session", style = TextStyle(color = ColorProvider(WidgetFg), fontSize = 12.sp))
            }
        }
    }
}

class CpuRamWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val t = DeviceTelemetryRepository(context).collect(false)
        val ramUsedMb = (t.ramTotalBytes - t.ramAvailBytes) / (1024 * 1024)
        val ramTotalMb = t.ramTotalBytes / (1024 * 1024)
        val ramPct = if (t.ramTotalBytes > 0) ((t.ramTotalBytes - t.ramAvailBytes) * 100 / t.ramTotalBytes) else 0
        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(launchIntent(context, "home")))) {
                WidgetCard("CPU & RAM", "${t.cpuCores} Cores · ${t.supportedAbis.firstOrNull() ?: "arm64"}") {
                    Text(text = "RAM: $ramUsedMb / $ramTotalMb MB ($ramPct%)", style = TextStyle(color = ColorProvider(WidgetFg), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(text = "CPU: ${t.cpuModel}", style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 11.sp))
                }
            }
        }
    }
}

class BatteryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val t = DeviceTelemetryRepository(context).collect(false)
        val tempStr = t.batteryTempCelsius?.let { String.format("%.1f°C", it) } ?: "N/A"
        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(launchIntent(context, "home")))) {
                WidgetCard("Battery", if (t.batteryCharging) "Charging · ${t.batteryHealth}" else t.batteryHealth) {
                    Text(text = "${t.batteryPercent}%", style = TextStyle(color = ColorProvider(WidgetFg), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    Text(text = "Temp: $tempStr · ${t.batteryVoltageMv} mV", style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 11.sp))
                }
            }
        }
    }
}

class StorageWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val t = DeviceTelemetryRepository(context).collect(false)
        val freeGb = t.internalFreeBytes / (1024.0 * 1024 * 1024)
        val totalGb = t.internalTotalBytes / (1024.0 * 1024 * 1024)
        val wsMb = t.workspaceBytes / (1024.0 * 1024)
        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(launchIntent(context, "files")))) {
                WidgetCard("Storage", "Internal & Workspace") {
                    Text(text = String.format("%.1f GB free / %.1f GB", freeGb, totalGb), style = TextStyle(color = ColorProvider(WidgetFg), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(text = String.format("Workspace: %.1f MB", wsMb), style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 11.sp))
                }
            }
        }
    }
}

class RunningTasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val activeSessions = TerminalSessionRegistry.activeSessionCount()
        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(launchIntent(context, "terminal")))) {
                WidgetCard("Running Tasks", "Active Background Shells") {
                    Text(text = "$activeSessions active PTY session${if (activeSessions == 1) "" else "s"}", style = TextStyle(color = ColorProvider(WidgetFg), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(text = "Tap to attach terminal", style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 11.sp))
                }
            }
        }
    }
}

class QuickCommandsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsRepository(context).state.value
        val cmds = listOf(settings.quickCommand1, settings.quickCommand2, settings.quickCommand3)
        provideContent {
            WidgetCard("Quick Commands", "Tap to run in shell") {
                cmds.forEach { cmd ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().background(WidgetSurface).padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(actionStartActivity(launchIntent(context, "terminal", cmd)))
                    ) { Text(text = "$ $cmd", style = TextStyle(color = ColorProvider(WidgetFg), fontSize = 11.sp)) }
                    Spacer(modifier = GlanceModifier.height(3.dp))
                }
            }
        }
    }
}

class NetworkStatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val t = DeviceTelemetryRepository(context).collect(false)
        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(launchIntent(context, "tools")))) {
                WidgetCard("Network Status", if (t.networkConnected) t.networkTransport else "Offline") {
                    Text(text = t.localIpv4, style = TextStyle(color = ColorProvider(WidgetFg), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(text = "Link: ${t.wifiLinkSpeedMbps?.let { "${it} Mbps" } ?: t.networkTransport}", style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 11.sp))
                }
            }
        }
    }
}

class TerminalLaunchWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TerminalLaunchWidget() }
class CpuRamWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CpuRamWidget() }
class BatteryWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BatteryWidget() }
class StorageWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StorageWidget() }
class RunningTasksWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RunningTasksWidget() }
class QuickCommandsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickCommandsWidget() }
class NetworkStatusWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NetworkStatusWidget() }

suspend fun refreshAllKestrelWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    listOf(TerminalLaunchWidget(), CpuRamWidget(), BatteryWidget(), StorageWidget(), RunningTasksWidget(), QuickCommandsWidget(), NetworkStatusWidget()).forEach { widget ->
        runCatching { manager.getGlanceIds(widget::class.java).forEach { widget.update(context, it) } }
    }
}
