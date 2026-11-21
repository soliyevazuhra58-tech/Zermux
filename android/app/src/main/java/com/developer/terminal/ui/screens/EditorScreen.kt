package com.developer.terminal.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.developer.terminal.KestrelApplication
import kotlinx.coroutines.launch
import java.io.File

private data class EditorTab(
    val file: File,
    val content: String,
    val dirty: Boolean = false
)

@Composable
fun EditorScreen(
    app: KestrelApplication,
    onRunInTerminal: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val homeDir = app.terminalManager.homeDir
    val tabs = remember { mutableStateListOf<EditorTab>() }
    var activeIndex by remember { mutableIntStateOf(0) }
    var explorerFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showExplorer by remember { mutableStateOf(true) }
    var showSearchBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("script.sh") }

    fun refreshWorkspaceFiles() {
        val list = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > 3) return
            dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })?.forEach { f ->
                if (!f.name.startsWith(".")) {
                    if (f.isDirectory) walk(f, depth + 1) else list += f
                }
            }
        }
        walk(homeDir, 0)
        explorerFiles = list
    }

    fun openFile(file: File) {
        val existing = tabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
        if (existing >= 0) {
            activeIndex = existing
            return
        }
        val text = runCatching { file.readText() }.getOrElse { "// Unable to read file: ${it.message}" }
        tabs.add(EditorTab(file, text, dirty = false))
        activeIndex = tabs.lastIndex
    }

    LaunchedEffect(Unit) {
        val starter = File(homeDir, "hello_kestrel.py")
        if (!starter.exists()) {
            starter.writeText(
                """
                #!/usr/bin/env python3
                # Kestrel Workspace Sample
                import os

                def greet(workspace: str) -> str:
                    return f"Workspace active at {workspace}"

                if __name__ == "__main__":
                    print(greet(os.getcwd()))
                """.trimIndent() + "\n"
            )
        }
        refreshWorkspaceFiles()
        if (tabs.isEmpty()) openFile(starter)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        val tab = tabs.getOrNull(activeIndex) ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                tab.file.writeText(tab.content)
                app.fileManager.exportFileToSafUri(tab.file, uri)
                statusMessage = "Exported ${tab.file.name} via SAF"
            }
        }
    }

    val currentTab = tabs.getOrNull(activeIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        // Editor Action Bar
        Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showExplorer = !showExplorer }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = "Toggle explorer", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showNewFileDialog = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = "New file", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        currentTab?.let { t ->
                            t.file.parentFile?.mkdirs()
                            t.file.writeText(t.content)
                            tabs[activeIndex] = t.copy(dirty = false)
                            statusMessage = "Saved ${t.file.name}"
                            refreshWorkspaceFiles()
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showSearchBar = !showSearchBar }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Search, contentDescription = "Find and replace", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { currentTab?.let { exportLauncher.launch(it.file.name) } },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = "Export file", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        currentTab?.let { t ->
                            t.file.writeText(t.content)
                            tabs[activeIndex] = t.copy(dirty = false)
                            val cmd = when (t.file.extension.lowercase()) {
                                "sh" -> "sh \"${t.file.absolutePath}\""
                                "py" -> "python3 \"${t.file.absolutePath}\" || python \"${t.file.absolutePath}\""
                                "js" -> "node \"${t.file.absolutePath}\""
                                else -> "cat \"${t.file.absolutePath}\""
                            }
                            onRunInTerminal(cmd)
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Run in terminal", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // Open Tabs Row
        if (tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEachIndexed { idx, tab ->
                    val selected = idx == activeIndex
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clickable { activeIndex = idx }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${tab.file.name}${if (tab.dirty) "*" else ""}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (tabs.size > 1) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(14.dp).clickable {
                                        tabs.removeAt(idx)
                                        activeIndex = activeIndex.coerceAtMost(tabs.lastIndex)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSearchBar) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    label = { Text("Find") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = { replaceQuery = it },
                    label = { Text("Replace") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        currentTab?.let { t ->
                            if (findQuery.isNotEmpty()) {
                                val next = t.content.replace(findQuery, replaceQuery)
                                tabs[activeIndex] = t.copy(content = next, dirty = true)
                                statusMessage = "Replaced occurrences"
                            }
                        }
                    }
                ) { Text("All") }
            }
        }

        // Main Split: Explorer + Code Area with Line Numbers
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (showExplorer) {
                Surface(
                    modifier = Modifier.width(168.dp).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("FILES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        explorerFiles.forEach { file ->
                            val selected = currentTab?.file?.absolutePath == file.absolutePath
                            Text(
                                text = file.relativeTo(homeDir).path,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openFile(file) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            if (currentTab != null) {
                val linesCount = currentTab.content.count { it == '\n' } + 1
                val ext = currentTab.file.extension.lowercase()
                val syntaxVisual = remember(ext) { CodeSyntaxTransformation(ext) }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF0D1117))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = (1..linesCount).joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFF6E7681),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    BasicTextField(
                        value = currentTab.content,
                        onValueChange = { next ->
                            val autoIndented = applySimpleAutoIndent(currentTab.content, next)
                            tabs[activeIndex] = currentTab.copy(content = autoIndented, dirty = true)
                        },
                        textStyle = TextStyle(
                            color = Color(0xFFE6EDF3),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = syntaxVisual,
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create Workspace File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("Filename (e.g. main.py, app.kt, index.html)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val clean = newFileName.trim().ifBlank { "untitled.txt" }
                    val file = File(homeDir, clean)
                    file.parentFile?.mkdirs()
                    if (!file.exists()) file.writeText("")
                    refreshWorkspaceFiles()
                    openFile(file)
                    showNewFileDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun applySimpleAutoIndent(previous: String, next: String): String {
    if (next.length == previous.length + 1 && next.endsWith("\n")) {
        val prevLines = previous.lines()
        val last = prevLines.lastOrNull().orEmpty()
        val indent = last.takeWhile { it == ' ' || it == '\t' }
        val extra = if (last.trimEnd().endsWith("{") || last.trimEnd().endsWith(":")) "    " else ""
        return next + indent + extra
    }
    return next
}

private class CodeSyntaxTransformation(private val ext: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val highlighted = buildAnnotatedString {
            append(s)
            val kwColor = Color(0xFFFF7B72)
            val strColor = Color(0xFFA5D6FF)
            val commentColor = Color(0xFF8B949E)
            val numColor = Color(0xFF79C0FF)

            val keywords = when (ext) {
                "py" -> "\\b(def|class|import|from|return|if|elif|else|for|while|with|as|try|except|True|False|None|in|not|and|or)\\b"
                "kt", "java" -> "\\b(package|import|fun|class|interface|object|val|var|return|if|else|when|for|while|private|public|override|suspend|true|false|null)\\b"
                "js", "ts", "jsx", "tsx" -> "\\b(const|let|var|function|return|if|else|for|while|import|export|from|async|await|class|interface|type|true|false|null)\\b"
                "c", "cpp", "h" -> "\\b(int|void|char|return|if|else|for|while|struct|typedef|include|define|const|static|NULL)\\b"
                else -> "\\b(function|return|if|else|for|while|true|false|null|export|echo)\\b"
            }

            Regex(keywords).findAll(s).forEach { m ->
                addStyle(SpanStyle(color = kwColor, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
            }
            Regex("\\b\\d+(\\.\\d+)?\\b").findAll(s).forEach { m ->
                addStyle(SpanStyle(color = numColor), m.range.first, m.range.last + 1)
            }
            Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'").findAll(s).forEach { m ->
                addStyle(SpanStyle(color = strColor), m.range.first, m.range.last + 1)
            }
            Regex("(//|#).*$", RegexOption.MULTILINE).findAll(s).forEach { m ->
                addStyle(SpanStyle(color = commentColor), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
