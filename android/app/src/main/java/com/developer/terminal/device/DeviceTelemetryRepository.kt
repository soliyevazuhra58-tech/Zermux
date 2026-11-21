package com.developer.terminal.device

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

data class PermissionStatus(val name: String, val granted: Boolean, val description: String)

data class DeviceTelemetry(
    val model: String, val manufacturer: String, val hardware: String, val androidVersion: String, val apiLevel: Int,
    val supportedAbis: List<String>, val cpuCores: Int, val cpuModel: String, val cpuFreqMhz: List<Int>,
    val ramTotalBytes: Long, val ramAvailBytes: Long, val internalTotalBytes: Long, val internalFreeBytes: Long,
    val workspaceBytes: Long, val batteryPercent: Int, val batteryCharging: Boolean, val batteryTempCelsius: Float?,
    val batteryVoltageMv: Int, val batteryHealth: String, val screenWidthPx: Int, val screenHeightPx: Int, val densityDpi: Int,
    val networkConnected: Boolean, val networkTransport: String, val localIpv4: String,
    val wifiLinkSpeedMbps: Int?, val wifiRssiDbm: Int?, val permissions: List<PermissionStatus>,
    val rootAvailable: Boolean?, val rootDetail: String
)

class DeviceTelemetryRepository(private val context: Context) {
    fun collect(checkRootExplicitly: Boolean = false): DeviceTelemetry {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val statInternal = StatFs(Environment.getDataDirectory().path)
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val tempC = if (rawTemp != Int.MIN_VALUE && rawTemp > 0) rawTemp / 10f else null
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val health = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val metrics = context.resources.displayMetrics
        val (wPx, hPx) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else metrics.widthPixels to metrics.heightPixels

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
        val wmWifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) wmWifi?.connectionInfo else null
        val (rootAvail, rootMsg) = if (checkRootExplicitly) checkSuBinary() else null to "Root check disabled (opt-in in Settings)"

        return DeviceTelemetry(
            Build.MODEL ?: "Unknown", Build.MANUFACTURER ?: "Unknown", Build.HARDWARE ?: "Unknown",
            Build.VERSION.RELEASE ?: "Unknown", Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            Runtime.getRuntime().availableProcessors(), readCpuModel(), readCpuFrequencies(Runtime.getRuntime().availableProcessors()),
            memInfo.totalMem, memInfo.availMem, statInternal.totalBytes, statInternal.availableBytes, folderSize(context.filesDir),
            pct, charging, tempC, voltageMv, health, wPx, hPx, metrics.densityDpi, connected, transport, findLocalIpv4(),
            wifiInfo?.linkSpeed?.takeIf { it > 0 }, wifiInfo?.rssi?.takeIf { it in -119..-1 }, collectPermissions(), rootAvail, rootMsg
        )
    }

    private fun readCpuModel(): String {
        val lines = runCatching { File("/proc/cpuinfo").readLines() }.getOrDefault(emptyList())
        return lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }?.substringAfter(":")?.trim()
            ?: lines.firstOrNull { it.startsWith("model name", ignoreCase = true) }?.substringAfter(":")?.trim()
            ?: (if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE)
    }

    private fun readCpuFrequencies(cores: Int): List<Int> = (0 until cores).map { idx ->
        val f = File("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq")
        val khz = runCatching { if (f.exists()) f.readText().trim().toIntOrNull() else null }.getOrNull()
        if (khz != null) khz / 1000 else 0
    }

    private fun findLocalIpv4(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "127.0.0.1"
    }.getOrDefault("127.0.0.1")

    private fun collectPermissions(): List<PermissionStatus> {
        val list = mutableListOf<PermissionStatus>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += PermissionStatus("Notifications (POST_NOTIFICATIONS)", ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED, "Displays active background terminal service status")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list += PermissionStatus("All Files Access (MANAGE_EXTERNAL_STORAGE)", Environment.isExternalStorageManager(), "Optional access to browse /storage/emulated/0 directly")
        } else {
            list += PermissionStatus("External Storage (READ/WRITE)", ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED, "Browse and edit shared device storage")
        }
        list += PermissionStatus("Network & Wi-Fi State", ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED, "Inspect network transport, IP address, and link speed")
        return list
    }

    private fun checkSuBinary(): Pair<Boolean, String> {
        val found = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su").firstOrNull { File(it).exists() }
        return if (found != null) true to "su binary found at $found (elevation requires Magisk/KernelSU approval)"
        else false to "Device is not rooted (no su binary detected)"
    }

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    }
}
