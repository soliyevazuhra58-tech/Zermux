package com.developer.terminal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.developer.terminal.data.prefs.AccentPalette
import com.developer.terminal.data.prefs.AppThemeMode

private val KestrelTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun KestrelTheme(
    themeMode: AppThemeMode,
    accent: AccentPalette,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accentColor = Color(accent.hex)

    val colors = if (isDark) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color(0xFF0D1117),
            primaryContainer = accentColor.copy(alpha = 0.16f),
            onPrimaryContainer = accentColor,
            secondary = Color(0xFF8B949E),
            onSecondary = Color(0xFF0D1117),
            background = Color(0xFF0B0F14),
            onBackground = Color(0xFFE6EDF3),
            surface = Color(0xFF11161D),
            onSurface = Color(0xFFE6EDF3),
            surfaceVariant = Color(0xFF161C25),
            onSurfaceVariant = Color(0xFF9CA3AF),
            outline = Color(0xFF242D3A),
            outlineVariant = Color(0xFF1D2430),
            error = Color(0xFFF87171)
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = accentColor.copy(alpha = 0.14f),
            onPrimaryContainer = Color(0xFF0F172A),
            secondary = Color(0xFF475569),
            onSecondary = Color.White,
            background = Color(0xFFF4F6F9),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFEFF3F8),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFD5DCE6),
            outlineVariant = Color(0xFFE2E8F0),
            error = Color(0xFFDC2626)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = KestrelTypography,
        content = content
    )
}
