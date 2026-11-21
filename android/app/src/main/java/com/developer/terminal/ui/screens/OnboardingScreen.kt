package com.developer.terminal.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OnboardingStep(
    val tag: String,
    val title: String,
    val summary: String,
    val highlights: List<Pair<ImageVector, String>>
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val steps = remember {
        listOf(
            OnboardingStep(
                tag = "01 / POSIX PTY ENGINE",
                title = "Native ARM64 Terminal Workspace",
                summary = "Kestrel spawns real interactive shell sessions via a compiled C++ JNI POSIX PTY bridge (/system/bin/sh + installed userland shells) with xterm-256color, truecolor, scrollback, and split panes.",
                highlights = listOf(
                    Icons.Rounded.Terminal to "Native libterminal_pty.so + libkestrel_utils.so ELF binaries",
                    Icons.Rounded.Code to "Multi-tab & split-pane VT100/ANSI emulator with persistent history"
                )
            ),
            OnboardingStep(
                tag = "02 / PACKAGE & FILE TOOLING",
                title = "Alpine aarch64 Packages & SAF Storage",
                summary = "Synchronize real APKINDEX repositories into your isolated \$PREFIX, edit multi-language source files with syntax highlighting, and manage internal or SAF folders.",
                highlights = listOf(
                    Icons.Rounded.Inventory2 to "Dependency-aware Alpine aarch64 APK package extraction",
                    Icons.Rounded.Folder to "Scoped workspace explorer with ZIP archive & SAF import/export"
                )
            ),
            OnboardingStep(
                tag = "03 / DEVICE & AUTOMATION",
                title = "Real Hardware Telemetry & Dev Suite",
                summary = "Inspect live CPU core frequencies, RAM, battery thermal sensors, network interfaces, JGit repositories, SSH sessions, SQLite databases, and 7 home-screen widgets.",
                highlights = listOf(
                    Icons.Rounded.Terminal to "Strict sandbox compliance; optional explicit su check only when enabled",
                    Icons.Rounded.Code to "Built-in HTTP client, local web server, JSON, Markdown & Room task scheduler"
                )
            )
        )
    }

    var page by remember { mutableIntStateOf(0) }
    val current = steps[page]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = current.tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = current.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = current.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                current.highlights.forEach { (icon, text) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${page + 1} of ${steps.size}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (page > 0) {
                        OutlinedButton(onClick = { page-- }) {
                            Text("Back")
                        }
                    } else {
                        OutlinedButton(onClick = onComplete) {
                            Text("Skip")
                        }
                    }
                    Button(
                        onClick = {
                            if (page < steps.lastIndex) page++ else onComplete()
                        }
                    ) {
                        Text(if (page < steps.lastIndex) "Continue" else "Launch Kestrel")
                    }
                }
            }
        }
    }
}
