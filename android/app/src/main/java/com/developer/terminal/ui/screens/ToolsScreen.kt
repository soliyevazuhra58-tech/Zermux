package com.developer.terminal.ui.screens

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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.developer.terminal.KestrelApplication
import com.developer.terminal.data.db.AutomationTaskEntity
import com.developer.terminal.data.db.EnvVarEntity
import com.developer.terminal.tools.KestrelPlugin
import com.developer.terminal.tools.OpenPortInfo
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun ToolsScreen(
    app: KestrelApplication,
    onRunInTerminal: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val tools = app.toolsEngine
    val dao = app.database.dao()
    val homeDir = app.terminalManager.homeDir

    val tabs = listOf("HTTP", "JSON", "Markdown", "SQLite", "Server/Ports", "Git/GitHub", "SSH", "Plugins/Tasks", "AI")
    var selectedTab by remember { mutableIntStateOf(0) }

    // HTTP state
    var httpMethod by remember { mutableStateOf("GET") }
    var httpUrl by remember { mutableStateOf("https://httpbin.org/get") }
    var httpHeaders by remember { mutableStateOf("Accept: application/json") }
    var httpBody by remember { mutableStateOf("{}") }
    var httpOutput by remember { mutableStateOf("") }

    // JSON state
    var jsonInput by remember { mutableStateOf("{\"kestrel\":\"1.0.0\",\"abi\":\"arm64-v8a\",\"features\":[\"pty\",\"jgit\",\"ssh\"]}") }
    var jsonResult by remember { mutableStateOf("") }

    // Markdown state
    var mdInput by remember { mutableStateOf("# Kestrel Workspace\n\n- POSIX PTY Terminal\n- Alpine `aarch64` Packages\n- JGit & JSch Client") }

    // SQLite state
    var dbPath by remember { mutableStateOf(File(homeDir, "sample_notes.db").absolutePath) }
    var sqliteSql by remember { mutableStateOf("SELECT * FROM notes;") }
    var sqliteOutput by remember { mutableStateOf("") }

    // Server & Ports
    val webState by tools.webServerState.collectAsState()
    var webPort by remember { mutableStateOf("8080") }
    var portsList by remember { mutableStateOf<List<OpenPortInfo>>(emptyList()) }

    // Git & GitHub
    var repoSubdir by remember { mutableStateOf("my_repo") }
    var cloneUrl by remember { mutableStateOf("") }
    var ghUser by remember { mutableStateOf("octocat") }
    var ghToken by remember { mutableStateOf("") }
    var commitMsg by remember { mutableStateOf("Initial commit from Kestrel") }
    var gitOutput by remember { mutableStateOf("") }
    var ghRepos by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // SSH
    var sshHost by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUser by remember { mutableStateOf("root") }
    var sshPass by remember { mutableStateOf("") }
    var sshCmd by remember { mutableStateOf("uname -a && uptime") }
    var sshOutput by remember { mutableStateOf("") }

    // Env, Templates, Plugins, Tasks
    val envVars by dao.observeEnvVars().collectAsState(initial = emptyList())
    val tasks by dao.observeTasks().collectAsState(initial = emptyList())
    var plugins by remember { mutableStateOf<List<KestrelPlugin>>(emptyList()) }
    var newEnvKey by remember { mutableStateOf("") }
    var newEnvVal by remember { mutableStateOf("") }
    var tplProjectName by remember { mutableStateOf("hello_app") }
    var newTaskName by remember { mutableStateOf("Disk Check") }
    var newTaskCmd by remember { mutableStateOf("df -h") }
    var toolsStatus by remember { mutableStateOf("") }

    // AI Assistant
    var aiPrompt by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 7) plugins = tools.loadPlugins()
        if (selectedTab == 4) portsList = tools.scanOpenTcpPorts()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            tabs.forEachIndexed { idx, label ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label) })
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    Text("OkHttp Request Tester", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("GET", "POST", "PUT", "DELETE", "HEAD").forEach { m ->
                            if (httpMethod == m) Button(onClick = {}) { Text(m) }
                            else OutlinedButton(onClick = { httpMethod = m }) { Text(m) }
                        }
                    }
                    OutlinedTextField(value = httpUrl, onValueChange = { httpUrl = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = httpHeaders, onValueChange = { httpHeaders = it }, label = { Text("Headers (Key: Value per line)") }, modifier = Modifier.fillMaxWidth())
                    if (httpMethod in listOf("POST", "PUT")) {
                        OutlinedTextField(value = httpBody, onValueChange = { httpBody = it }, label = { Text("JSON Body") }, modifier = Modifier.fillMaxWidth())
                    }
                    Button(onClick = {
                        scope.launch {
                            tools.executeHttpRequest(httpMethod, httpUrl, httpHeaders, httpBody).fold(
                                onSuccess = { r ->
                                    httpOutput = "HTTP ${r.statusCode} ${r.statusMessage} (${r.latencyMs} ms)\n\n${r.body}"
                                },
                                onFailure = { httpOutput = "Error: ${it.message}" }
                            )
                        }
                    }) { Text("Send Request") }
                    if (httpOutput.isNotBlank()) CodeOutputBox(httpOutput)
                }

                1 -> {
                    Text("JSON Formatter & Validator", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = jsonInput, onValueChange = { jsonInput = it }, label = { Text("Raw JSON") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            jsonResult = tools.formatJson(jsonInput).getOrElse { "Invalid JSON: ${it.message}" }
                        }) { Text("Format (2 spaces)") }
                        OutlinedButton(onClick = {
                            jsonResult = tools.minifyJson(jsonInput).getOrElse { "Invalid JSON: ${it.message}" }
                        }) { Text("Minify") }
                    }
                    if (jsonResult.isNotBlank()) CodeOutputBox(jsonResult)
                }

                2 -> {
                    Text("Markdown Preview", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = mdInput, onValueChange = { mdInput = it }, label = { Text("Markdown Source") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            mdInput.lines().forEach { line ->
                                when {
                                    line.startsWith("# ") -> Text(line.drop(2), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    line.startsWith("## ") -> Text(line.drop(3), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    line.startsWith("- ") -> Text("• ${line.drop(2)}", style = MaterialTheme.typography.bodyMedium)
                                    else -> Text(line, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                3 -> {
                    Text("SQLite Browser", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = dbPath, onValueChange = { dbPath = it }, label = { Text("Database File Path") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sqliteSql, onValueChange = { sqliteSql = it }, label = { Text("SQL Query") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                tools.querySqlite(File(dbPath), sqliteSql).fold(
                                    onSuccess = { res ->
                                        sqliteOutput = buildString {
                                            appendLine(res.message)
                                            if (res.columns.isNotEmpty()) appendLine(res.columns.joinToString(" | "))
                                            res.rows.forEach { appendLine(it.joinToString(" | ")) }
                                        }
                                    },
                                    onFailure = { sqliteOutput = "SQL Error: ${it.message}" }
                                )
                            }
                        }) { Text("Execute SQL") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                tools.listSqliteTables(File(dbPath)).fold(
                                    onSuccess = { tables -> sqliteOutput = "Tables: ${tables.joinToString()}" },
                                    onFailure = { sqliteOutput = "Error: ${it.message}" }
                                )
                            }
                        }) { Text("List Tables") }
                    }
                    if (sqliteOutput.isNotBlank()) CodeOutputBox(sqliteOutput)
                }

                4 -> {
                    Text("Local Static Web Server", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = webPort, onValueChange = { webPort = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
                        if (webState.running) {
                            Button(onClick = { tools.stopWebServer() }) { Text("Stop :${webState.port}") }
                        } else {
                            Button(onClick = { tools.startWebServer(webPort.toIntOrNull() ?: 8080, homeDir) }) { Text("Start Server") }
                        }
                    }
                    Text(
                        if (webState.running) "Serving ${webState.rootDir} at http://127.0.0.1:${webState.port} (${webState.requestCount} reqs)"
                        else webState.lastError ?: "Server stopped",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active TCP Sockets (/proc/net/tcp)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { scope.launch { portsList = tools.scanOpenTcpPorts() } }) { Text("Scan") }
                    }
                    CodeOutputBox(
                        if (portsList.isEmpty()) "No active sockets visible in procfs."
                        else portsList.joinToString("\n") { "${it.protocol} :${it.port} (${it.state})" }
                    )
                }

                5 -> {
                    Text("JGit & GitHub Client", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = repoSubdir, onValueChange = { repoSubdir = it }, label = { Text("Workspace Repo Folder (\$HOME/<dir>)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cloneUrl, onValueChange = { cloneUrl = it }, label = { Text("Clone URL (https://github.com/owner/repo.git)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = commitMsg, onValueChange = { commitMsg = it }, label = { Text("Commit Message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Button(onClick = {
                            scope.launch { gitOutput = tools.gitInit(File(homeDir, repoSubdir).apply { mkdirs() }).getOrElse { it.message ?: "Err" } }
                        }) { Text("git init") }
                        OutlinedButton(onClick = {
                            scope.launch { gitOutput = tools.gitClone(cloneUrl, File(homeDir, repoSubdir), ghToken).getOrElse { it.message ?: "Err" } }
                        }) { Text("clone") }
                        OutlinedButton(onClick = {
                            scope.launch { gitOutput = tools.gitStatusAndLog(File(homeDir, repoSubdir)).getOrElse { it.message ?: "Err" } }
                        }) { Text("status/log") }
                        OutlinedButton(onClick = {
                            scope.launch { gitOutput = tools.gitCommitAll(File(homeDir, repoSubdir), commitMsg).getOrElse { it.message ?: "Err" } }
                        }) { Text("commit -am") }
                    }
                    OutlinedTextField(value = ghUser, onValueChange = { ghUser = it }, label = { Text("GitHub Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ghToken, onValueChange = { ghToken = it }, label = { Text("Optional Personal Access Token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = {
                        scope.launch {
                            tools.fetchGithubRepos(ghUser, ghToken).fold(
                                onSuccess = { ghRepos = it; gitOutput = "Loaded ${it.size} GitHub repositories" },
                                onFailure = { gitOutput = it.message ?: "GitHub error" }
                            )
                        }
                    }) { Text("Fetch GitHub Repositories") }
                    ghRepos.forEach { (name, url) ->
                        OutlinedButton(onClick = { cloneUrl = url; repoSubdir = name.substringAfter("/") }) {
                            Text("Clone $name", fontFamily = FontFamily.Monospace)
                        }
                    }
                    if (gitOutput.isNotBlank()) CodeOutputBox(gitOutput)
                }

                6 -> {
                    Text("JSch SSH Remote Exec", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sshHost, onValueChange = { sshHost = it }, label = { Text("Host") }, singleLine = true, modifier = Modifier.weight(2f))
                        OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sshUser, onValueChange = { sshUser = it }, label = { Text("User") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = sshPass, onValueChange = { sshPass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = sshCmd, onValueChange = { sshCmd = it }, label = { Text("Remote Command") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch {
                            sshOutput = tools.executeSshCommand(sshHost, sshPort.toIntOrNull() ?: 22, sshUser, sshPass, sshCmd).getOrElse { "SSH Error: ${it.message}" }
                        }
                    }) { Text("Execute via SSH") }
                    if (sshOutput.isNotBlank()) CodeOutputBox(sshOutput)
                }

                7 -> {
                    Text("Project Templates", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = tplProjectName, onValueChange = { tplProjectName = it }, label = { Text("Project Folder Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("Static Web", "Shell Automation", "Python Starter", "C Makefile").forEach { tpl ->
                            OutlinedButton(onClick = {
                                scope.launch {
                                    tools.createProjectTemplate(tpl, tplProjectName).onSuccess {
                                        toolsStatus = "Created $tpl in ${it.name}"
                                    }
                                }
                            }) { Text(tpl) }
                        }
                    }

                    Text("Environment Variables (injected into new shells)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = newEnvKey, onValueChange = { newEnvKey = it }, label = { Text("KEY") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = newEnvVal, onValueChange = { newEnvVal = it }, label = { Text("VALUE") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            if (newEnvKey.isNotBlank()) {
                                scope.launch { dao.upsertEnvVar(EnvVarEntity(newEnvKey.trim(), newEnvVal)) }
                                newEnvKey = ""; newEnvVal = ""
                            }
                        }) { Text("Add") }
                    }
                    envVars.forEach { ev ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ev.key}=${ev.value}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { scope.launch { dao.deleteEnvVar(ev.key) } }) { Text("Remove") }
                        }
                    }

                    Text("JSON Plugins (~/.kestrel/plugins)", style = MaterialTheme.typography.titleMedium)
                    plugins.forEach { p ->
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Medium)
                                    Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(onClick = { onRunInTerminal(p.command) }) { Text("Run") }
                            }
                        }
                    }

                    Text("Automation Task Scheduler", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = newTaskName, onValueChange = { newTaskName = it }, label = { Text("Task Name") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = newTaskCmd, onValueChange = { newTaskCmd = it }, label = { Text("Shell Cmd") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            scope.launch {
                                dao.upsertTask(AutomationTaskEntity(UUID.randomUUID().toString(), newTaskName, newTaskCmd, 15, true, 0L, ""))
                            }
                        }) { Text("Save") }
                    }
                    tasks.forEach { t ->
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${t.name} (every ${t.intervalMinutes}m)", fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { scope.launch { tools.runScheduledTaskNow(t) } }) { Text("Run Now") }
                                        OutlinedButton(onClick = { scope.launch { dao.deleteTask(t.id) } }) { Text("Delete") }
                                    }
                                }
                                Text("$ ${t.command}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                if (t.lastOutput.isNotBlank()) {
                                    Text(t.lastOutput, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (toolsStatus.isNotBlank()) Text(toolsStatus, color = MaterialTheme.colorScheme.primary)
                }

                8 -> {
                    Text("Optional Coding Assistant (User API Key)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Configure your own Gemini API key & endpoint in Settings. Requests go directly from your device using your key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(value = aiPrompt, onValueChange = { aiPrompt = it }, label = { Text("Ask for shell command, regex, or code snippet...") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch {
                            aiResponse = tools.askAiAssistant(aiPrompt).getOrElse { it.message ?: "Request failed" }
                        }
                    }) { Text("Send Prompt") }
                    if (aiResponse.isNotBlank()) CodeOutputBox(aiResponse)
                }
            }
        }
    }
}

@Composable
private fun CodeOutputBox(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}
