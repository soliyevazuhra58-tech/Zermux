package com.developer.terminal.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode { DARK, LIGHT, SYSTEM }
enum class AccentPalette(val hex: Long, val label: String) {
    AMBER(0xFFE5A00D, "Amber"),
    TEAL(0xFF2DD4BF, "Teal"),
    COBALT(0xFF3B82F6, "Cobalt"),
    EMERALD(0xFF10B981, "Emerald"),
    ROSE(0xFFF43F5E, "Rose")
}

enum class TerminalColorScheme(
    val id: String,
    val label: String,
    val bgHex: Long,
    val fgHex: Long,
    val cursorHex: Long,
    val selectionHex: Long
) {
    KESTREL_CHARCOAL("charcoal", "Kestrel Charcoal", 0xFF0D1117, 0xFFE6EDF3, 0xFFE5A00D, 0xFF264F78),
    SLATE_DAYLIGHT("daylight", "Slate Daylight", 0xFFF8FAFC, 0xFF0F172A, 0xFF2563EB, 0xFFBFDBFE),
    SOLARIZED_DARK("solarized", "Solarized Dark", 0xFF002B36, 0xFF93A1A1, 0xFFB58900, 0xFF073642),
    NORD_FROST("nord", "Nord Frost", 0xFF2E3440, 0xFFD8DEE9, 0xFF88C0D0, 0xFF434C5E),
    MONOKAI_PRO("monokai", "Monokai Pro", 0xFF1E1F1C, 0xFFF8F8F2, 0xFFA6E22E, 0xFF49483E)
}

data class AppSettingsState(
    val onboardingCompleted: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val accent: AccentPalette = AccentPalette.AMBER,
    val terminalScheme: TerminalColorScheme = TerminalColorScheme.KESTREL_CHARCOAL,
    val terminalFontSizeSp: Int = 13,
    val terminalFontFamily: String = "Monospace",
    val scrollbackLines: Int = 3000,
    val keepScreenAwake: Boolean = false,
    val backgroundServiceEnabled: Boolean = true,
    val rootIntegrationOptIn: Boolean = false,
    val aiApiKey: String = "",
    val aiEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
    val cloudSyncWebhookUrl: String = "",
    val quickCommand1: String = "uname -a",
    val quickCommand2: String = "df -h",
    val quickCommand3: String = "ls -la"
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("kestrel_settings", Context.MODE_PRIVATE)
    private val secretsPrefs: SharedPreferences =
        context.getSharedPreferences("kestrel_secrets", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    private fun loadState(): AppSettingsState {
        val themeMode = runCatching {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.DARK.name)!!)
        }.getOrDefault(AppThemeMode.DARK)
        val accent = runCatching {
            AccentPalette.valueOf(prefs.getString("accent", AccentPalette.AMBER.name)!!)
        }.getOrDefault(AccentPalette.AMBER)
        val scheme = runCatching {
            TerminalColorScheme.valueOf(prefs.getString("terminal_scheme", TerminalColorScheme.KESTREL_CHARCOAL.name)!!)
        }.getOrDefault(TerminalColorScheme.KESTREL_CHARCOAL)

        return AppSettingsState(
            onboardingCompleted = prefs.getBoolean("onboarding_completed", false),
            themeMode = themeMode,
            accent = accent,
            terminalScheme = scheme,
            terminalFontSizeSp = prefs.getInt("terminal_font_size", 13),
            terminalFontFamily = prefs.getString("terminal_font_family", "Monospace") ?: "Monospace",
            scrollbackLines = prefs.getInt("scrollback_lines", 3000),
            keepScreenAwake = prefs.getBoolean("keep_screen_awake", false),
            backgroundServiceEnabled = prefs.getBoolean("background_service", true),
            rootIntegrationOptIn = prefs.getBoolean("root_opt_in", false),
            aiApiKey = secretsPrefs.getString("ai_api_key", "") ?: "",
            aiEndpoint = prefs.getString(
                "ai_endpoint",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
            ) ?: "",
            cloudSyncWebhookUrl = secretsPrefs.getString("cloud_sync_url", "") ?: "",
            quickCommand1 = prefs.getString("quick_cmd_1", "uname -a") ?: "uname -a",
            quickCommand2 = prefs.getString("quick_cmd_2", "df -h") ?: "df -h",
            quickCommand3 = prefs.getString("quick_cmd_3", "ls -la") ?: "ls -la"
        )
    }

    fun update(block: (AppSettingsState) -> AppSettingsState) {
        val next = block(_state.value)
        _state.value = next
        prefs.edit()
            .putBoolean("onboarding_completed", next.onboardingCompleted)
            .putString("theme_mode", next.themeMode.name)
            .putString("accent", next.accent.name)
            .putString("terminal_scheme", next.terminalScheme.name)
            .putInt("terminal_font_size", next.terminalFontSizeSp)
            .putString("terminal_font_family", next.terminalFontFamily)
            .putInt("scrollback_lines", next.scrollbackLines)
            .putBoolean("keep_screen_awake", next.keepScreenAwake)
            .putBoolean("background_service", next.backgroundServiceEnabled)
            .putBoolean("root_opt_in", next.rootIntegrationOptIn)
            .putString("ai_endpoint", next.aiEndpoint)
            .putString("quick_cmd_1", next.quickCommand1)
            .putString("quick_cmd_2", next.quickCommand2)
            .putString("quick_cmd_3", next.quickCommand3)
            .apply()
        secretsPrefs.edit()
            .putString("ai_api_key", next.aiApiKey)
            .putString("cloud_sync_url", next.cloudSyncWebhookUrl)
            .apply()
    }
}
