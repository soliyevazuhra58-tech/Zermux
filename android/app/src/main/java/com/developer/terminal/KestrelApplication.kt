package com.developer.terminal

import android.app.Application
import com.developer.terminal.data.db.KestrelDatabase
import com.developer.terminal.data.prefs.SettingsRepository
import com.developer.terminal.device.DeviceTelemetryRepository
import com.developer.terminal.files.FileManagerRepository
import com.developer.terminal.packages.PackageManagerEngine
import com.developer.terminal.terminal.TerminalSessionManager
import com.developer.terminal.tools.DeveloperToolsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KestrelApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: KestrelDatabase private set
    lateinit var settings: SettingsRepository private set
    lateinit var terminalManager: TerminalSessionManager private set
    lateinit var packageEngine: PackageManagerEngine private set
    lateinit var deviceTelemetry: DeviceTelemetryRepository private set
    lateinit var fileManager: FileManagerRepository private set
    lateinit var toolsEngine: DeveloperToolsEngine private set

    override fun onCreate() {
        super.onCreate()
        database = KestrelDatabase.getInstance(this)
        settings = SettingsRepository(this)
        terminalManager = TerminalSessionManager(this, database, settings)
        packageEngine = PackageManagerEngine(this, database)
        deviceTelemetry = DeviceTelemetryRepository(this)
        fileManager = FileManagerRepository(this)
        toolsEngine = DeveloperToolsEngine(this, database, settings)

        appScope.launch { packageEngine.ensureDefaultReposAndNativeInventory() }
    }
}
