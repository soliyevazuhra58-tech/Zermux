package com.developer.terminal

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.developer.terminal.ui.screens.EditorScreen
import com.developer.terminal.ui.screens.FilesScreen
import com.developer.terminal.ui.screens.HomeScreen
import com.developer.terminal.ui.screens.OnboardingScreen
import com.developer.terminal.ui.screens.PackagesScreen
import com.developer.terminal.ui.screens.SettingsScreen
import com.developer.terminal.ui.screens.TerminalScreen
import com.developer.terminal.ui.screens.ToolsScreen
import com.developer.terminal.ui.theme.KestrelTheme

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val Destinations = listOf(
    NavDestination("home", "Home", Icons.Rounded.Home),
    NavDestination("terminal", "Terminal", Icons.Rounded.Terminal),
    NavDestination("editor", "Editor", Icons.Rounded.Code),
    NavDestination("packages", "Packages", Icons.Rounded.Inventory2),
    NavDestination("files", "Files", Icons.Rounded.Folder),
    NavDestination("tools", "Tools", Icons.Rounded.Build),
    NavDestination("settings", "Settings", Icons.Rounded.Settings)
)

class MainActivity : ComponentActivity() {
    private var currentRoute by mutableStateOf("home")
    private var pendingQuickCommand by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntentExtras(intent)

        val app = application as KestrelApplication

        setContent {
            val settings by app.settings.state.collectAsState()

            LaunchedEffect(settings.keepScreenAwake) {
                if (settings.keepScreenAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            LaunchedEffect(pendingQuickCommand) {
                val cmd = pendingQuickCommand
                if (!cmd.isNullOrBlank()) {
                    currentRoute = "terminal"
                    app.terminalManager.runCommandInActiveSession(cmd)
                    pendingQuickCommand = null
                }
            }

            KestrelTheme(themeMode = settings.themeMode, accent = settings.accent) {
                if (!settings.onboardingCompleted) {
                    OnboardingScreen(
                        onComplete = {
                            app.settings.update { it.copy(onboardingCompleted = true) }
                        }
                    )
                } else {
                    KestrelShellScaffold(
                        app = app,
                        currentRoute = currentRoute,
                        onSelectRoute = { currentRoute = it }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        val route = intent?.getStringExtra("route")
        if (!route.isNullOrBlank() && Destinations.any { it.route == route }) {
            currentRoute = route
        }
        val cmd = intent?.getStringExtra("quick_command")
        if (!cmd.isNullOrBlank()) {
            pendingQuickCommand = cmd
        }
    }
}

@Composable
private fun KestrelShellScaffold(
    app: KestrelApplication,
    currentRoute: String,
    onSelectRoute: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 640.dp

        Scaffold(
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        Destinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { onSelectRoute(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                alwaysShowLabel = false
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        Destinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { onSelectRoute(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentRoute) {
                        "home" -> HomeScreen(
                            app = app,
                            onNavigate = onSelectRoute,
                            onRunQuickCommand = { cmd ->
                                onSelectRoute("terminal")
                                app.terminalManager.runCommandInActiveSession(cmd)
                            }
                        )
                        "terminal" -> TerminalScreen(app = app)
                        "editor" -> EditorScreen(
                            app = app,
                            onRunInTerminal = { cmd ->
                                onSelectRoute("terminal")
                                app.terminalManager.runCommandInActiveSession(cmd)
                            }
                        )
                        "packages" -> PackagesScreen(app = app)
                        "files" -> FilesScreen(app = app)
                        "tools" -> ToolsScreen(
                            app = app,
                            onRunInTerminal = { cmd ->
                                onSelectRoute("terminal")
                                app.terminalManager.runCommandInActiveSession(cmd)
                            }
                        )
                        "settings" -> SettingsScreen(app = app)
                    }
                }
            }
        }
    }
}
