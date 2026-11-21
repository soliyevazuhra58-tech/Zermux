package com.developer.terminal.tools

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.developer.terminal.data.db.AutomationTaskEntity
import com.developer.terminal.data.db.KestrelDatabase
import com.developer.terminal.data.prefs.SettingsRepository
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class HttpResponseResult(val statusCode: Int, val statusMessage: String, val latencyMs: Long, val headers: Map<String, String>, val body: String)
data class SqliteQueryResult(val columns: List<String>, val rows: List<List<String>>, val message: String)
data class OpenPortInfo(val protocol: String, val localAddress: String, val port: Int, val state: String)
data class KestrelPlugin(val id: String, val name: String, val description: String, val command: String, val filePath: String)
data class WebServerState(val running: Boolean = false, val port: Int = 8080, val rootDir: String = "", val requestCount: Int = 0, val lastError: String? = null)

class DeveloperToolsEngine(
    private val context: Context,
    private val db: KestrelDatabase,
    private val settingsRepo: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val homeDir = File(context.filesDir, "home").apply { mkdirs() }
    private val pluginsDir = File(homeDir, ".kestrel/plugins").apply { mkdirs() }

    private val _webServerState = MutableStateFlow(WebServerState(rootDir = homeDir.absolutePath))
    val webServerState: StateFlow<WebServerState> = _webServerState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var schedulerJob: Job? = null

    init { ensureSamplePluginAndDb(); startTaskSchedulerLoop() }

    private fun ensureSamplePluginAndDb() {
        val samplePlugin = File(pluginsDir, "workspace_stats.json")
        if (!samplePlugin.exists()) {
            samplePlugin.writeText(JSONObject().apply {
                put("id", "workspace_stats"); put("name", "Workspace Disk Summary")
                put("description", "Prints disk usage and file count in \$HOME")
                put("command", "du -sh \"\$HOME\" && ls -la \"\$HOME\"")
            }.toString(2))
        }
        val sampleDb = File(homeDir, "sample_notes.db")
        if (!sampleDb.exists()) {
            runCatching {
                SQLiteDatabase.openOrCreateDatabase(sampleDb, null).use { sqlite ->
                    sqlite.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, tag TEXT, created_at TEXT)")
                    sqlite.execSQL("INSERT INTO notes (title, tag, created_at) VALUES ('Kestrel Workspace Initialized', 'system', datetime('now'))")
                }
            }
        }
    }

    suspend fun executeHttpRequest(method: String, url: String, headersText: String, bodyText: String): Result<HttpResponseResult> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url.trim())
            headersText.lines().filter { it.contains(":") }.forEach { line ->
                val idx = line.indexOf(":")
                val k = line.substring(0, idx).trim(); val v = line.substring(idx + 1).trim()
                if (k.isNotEmpty()) builder.addHeader(k, v)
            }
            val upper = method.uppercase()
            val reqBody = if (upper in listOf("POST", "PUT", "PATCH")) bodyText.toRequestBody("application/json; charset=utf-8".toMediaType()) else null
            builder.method(upper, reqBody)
            val t0 = System.nanoTime()
            httpClient.newCall(builder.build()).execute().use { resp ->
                HttpResponseResult(resp.code, resp.message, (System.nanoTime() - t0) / 1_000_000L, resp.headers.toMultimap().mapValues { it.value.joinToString(", ") }, resp.body?.string() ?: "")
            }
        }
    }

    fun formatJson(raw: String, indentSpaces: Int = 2): Result<String> = runCatching {
        val t = raw.trim()
        when { t.startsWith("{") -> JSONObject(t).toString(indentSpaces); t.startsWith("[") -> JSONArray(t).toString(indentSpaces); else -> error("Input must start with '{' or '['") }
    }

    fun minifyJson(raw: String): Result<String> = runCatching {
        val t = raw.trim()
        when { t.startsWith("{") -> JSONObject(t).toString(); t.startsWith("[") -> JSONArray(t).toString(); else -> error("Input must start with '{' or '['") }
    }

    suspend fun listSqliteTables(dbFile: File): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                val list = mutableListOf<String>()
                sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null).use { c -> while (c.moveToNext()) list += c.getString(0) }
                list
            }
        }
    }

    suspend fun querySqlite(dbFile: File, sql: String): Result<SqliteQueryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = sql.trim()
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
                if (trimmed.startsWith("SELECT", true) || trimmed.startsWith("PRAGMA", true) || trimmed.startsWith("WITH", true)) {
                    sqlite.rawQuery(trimmed, null).use { cursor ->
                        val cols = cursor.columnNames.toList()
                        val rows = mutableListOf<List<String>>()
                        var limit = 0
                        while (cursor.moveToNext() && limit < 250) { rows += (0 until cols.size).map { idx -> cursor.getString(idx) ?: "NULL" }; limit++ }
                        SqliteQueryResult(cols, rows, "${rows.size} row(s) returned")
                    }
                } else { sqlite.execSQL(trimmed); SqliteQueryResult(emptyList(), emptyList(), "Statement executed successfully") }
            }
        }
    }

    suspend fun gitInit(dir: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching { Git.init().setDirectory(dir).call().use { "Initialized Git repository in ${it.repository.directory.absolutePath}" } }
    }

    suspend fun gitClone(repoUrl: String, destDir: File, token: String = ""): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cmd = Git.cloneRepository().setURI(repoUrl.trim()).setDirectory(destDir)
            if (token.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider("git", token.trim()))
            cmd.call().use { "Cloned $repoUrl into ${it.repository.directory.parentFile?.name}" }
        }
    }

    suspend fun gitStatusAndLog(repoDir: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(repoDir).use { git ->
                val status = git.status().call()
                buildString {
                    appendLine("Branch: ${git.repository.branch}")
                    appendLine("Clean: ${status.isClean}")
                    if (status.modified.isNotEmpty()) appendLine("Modified: ${status.modified.joinToString()}")
                    if (status.untracked.isNotEmpty()) appendLine("Untracked: ${status.untracked.joinToString()}")
                    appendLine("\nRecent Commits:")
                    runCatching { git.log().setMaxCount(8).call().forEach { appendLine("• ${it.id.abbreviate(7).name()} ${it.shortMessage}") } }
                }.trim()
            }
        }
    }

    suspend fun gitCommitAll(repoDir: File, message: String, authorName: String = "Kestrel Developer", authorEmail: String = "dev@kestrel.local"): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(repoDir).use { git ->
                git.add().addFilepattern(".").call()
                val rev = git.commit().setMessage(message.ifBlank { "Update workspace" }).setAuthor(authorName, authorEmail).call()
                "Committed ${rev.id.abbreviate(7).name()}: ${rev.shortMessage}"
            }
        }
    }

    suspend fun fetchGithubRepos(username: String, token: String = ""): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (username.isBlank() && token.isNotBlank()) "https://api.github.com/user/repos?sort=updated&per_page=20" else "https://api.github.com/users/${username.trim()}/repos?sort=updated&per_page=20"
            val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").apply { if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}") }.build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("GitHub HTTP ${resp.code}: ${resp.message}")
                val arr = JSONArray(resp.body?.string() ?: "[]")
                (0 until arr.length()).map { i -> arr.getJSONObject(i).let { it.getString("full_name") to it.getString("clone_url") } }
            }
        }
    }

    suspend fun executeSshCommand(host: String, port: Int, username: String, password: String, command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = JSch().getSession(username.trim(), host.trim(), port)
            session.setPassword(password)
            session.setConfig(Properties().apply { put("StrictHostKeyChecking", "no") })
            session.connect(12000)
            try {
                val ch = session.openChannel("exec") as ChannelExec
                ch.setCommand(command); ch.inputStream = null
                val out = ch.inputStream; val err = ch.errStream
                ch.connect(10000)
                val stdout = out.bufferedReader().readText(); val stderr = err.bufferedReader().readText()
                ch.disconnect()
                buildString {
                    if (stdout.isNotBlank()) append(stdout)
                    if (stderr.isNotBlank()) { if (isNotEmpty()) append("\n"); append("[stderr]\n").append(stderr) }
                    if (isEmpty()) append("(Command completed with exit status ${ch.exitStatus})")
                }
            } finally { session.disconnect() }
        }
    }

    fun startWebServer(port: Int, rootDir: File) {
        stopWebServer()
        serverJob = scope.launch {
            try {
                val ss = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress("127.0.0.1", port)) }
                serverSocket = ss
                _webServerState.value = WebServerState(true, port, rootDir.absolutePath, 0)
                while (isActive && !ss.isClosed) {
                    val sock = ss.accept()
                    launch { handleHttpClient(sock, rootDir) }
                }
            } catch (t: Throwable) {
                if (_webServerState.value.running) _webServerState.value = _webServerState.value.copy(running = false, lastError = t.message)
            }
        }
    }

    fun stopWebServer() {
        runCatching { serverSocket?.close() }; serverSocket = null; serverJob?.cancel(); serverJob = null
        _webServerState.value = _webServerState.value.copy(running = false)
    }

    private fun handleHttpClient(socket: Socket, rootDir: File) {
        socket.use { sock ->
            val reqLine = BufferedReader(InputStreamReader(sock.getInputStream())).readLine() ?: return
            val rel = (reqLine.split(" ").getOrNull(1)?.substringBefore("?") ?: "/").trimStart('/').ifEmpty { "index.html" }
            val target = File(rootDir, rel).canonicalFile
            val out = sock.getOutputStream()
            if (!target.path.startsWith(rootDir.canonicalPath)) {
                val b = "403 Forbidden".toByteArray(); out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: ${b.size}\r\n\r\n".toByteArray()); out.write(b); return
            }
            if (target.isDirectory) {
                val html = buildString {
                    append("<html><body style='font-family:monospace;background:#0d1117;color:#e6edf3;padding:24px'><h2>Index of /$rel</h2><ul>")
                    target.listFiles()?.sortedBy { it.name }?.forEach { val s = if (it.isDirectory) "/" else ""; append("<li><a style='color:#e5a00d' href='${it.name}$s'>${it.name}$s</a></li>") }
                    append("</ul></body></html>")
                }.toByteArray()
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.size}\r\n\r\n".toByteArray()); out.write(html)
            } else if (target.exists() && target.isFile) {
                val mime = when (target.extension.lowercase()) { "html", "htm" -> "text/html"; "css" -> "text/css"; "js" -> "application/javascript"; "json" -> "application/json"; "md", "txt" -> "text/plain"; else -> "application/octet-stream" }
                val bytes = target.readBytes()
                out.write("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray()); out.write(bytes)
            } else {
                val b = "404 Not Found".toByteArray(); out.write("HTTP/1.1 404 Not Found\r\nContent-Length: ${b.size}\r\n\r\n".toByteArray()); out.write(b)
            }
            _webServerState.value = _webServerState.value.copy(requestCount = _webServerState.value.requestCount + 1)
        }
    }

    suspend fun scanOpenTcpPorts(): List<OpenPortInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<OpenPortInfo>()
        listOf("/proc/net/tcp" to "TCP", "/proc/net/tcp6" to "TCP6").forEach { (path, proto) ->
            runCatching {
                File(path).readLines().drop(1).forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size >= 4) {
                        val lp = cols[1].split(":")
                        val port = lp.getOrNull(1)?.toIntOrNull(16) ?: 0
                        val st = when (cols[3]) { "0A" -> "LISTEN"; "01" -> "ESTABLISHED"; "06" -> "TIME_WAIT"; else -> cols[3] }
                        if (port > 0) result += OpenPortInfo(proto, lp[0], port, st)
                    }
                }
            }
        }
        result.distinctBy { "${it.protocol}:${it.port}:${it.state}" }.sortedBy { it.port }
    }

    suspend fun createProjectTemplate(templateType: String, projectName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = projectName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "project_${System.currentTimeMillis()}" }
            val dir = File(homeDir, clean).apply { mkdirs() }
            when (templateType) {
                "Static Web" -> {
                    File(dir, "index.html").writeText("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>$clean</title><link rel=\"stylesheet\" href=\"style.css\"></head><body><h1>$clean</h1><script src=\"app.js\"></script></body></html>\n")
                    File(dir, "style.css").writeText("body{font-family:system-ui;background:#0d1117;color:#e6edf3;padding:2rem}\n")
                    File(dir, "app.js").writeText("console.log('Loaded $clean');\n")
                }
                "Shell Automation" -> {
                    File(dir, "run.sh").writeText("#!/system/bin/sh\necho \"Running $clean on $(uname -m)\"\nls -la\n")
                    File(dir, "README.md").writeText("# $clean\nRun with `sh run.sh` inside Kestrel Terminal.\n")
                }
                "Python Starter" -> File(dir, "main.py").writeText("print('Hello from $clean')\n")
                "C Makefile" -> {
                    File(dir, "main.c").writeText("#include <stdio.h>\nint main(void){printf(\"Hello from $clean\\n\");return 0;}\n")
                    File(dir, "Makefile").writeText("all:\n\tcc -O2 main.c -o app\n")
                }
            }
            dir
        }
    }

    suspend fun loadPlugins(): List<KestrelPlugin> = withContext(Dispatchers.IO) {
        pluginsDir.listFiles { f -> f.extension == "json" }?.mapNotNull { file ->
            runCatching {
                val obj = JSONObject(file.readText())
                KestrelPlugin(obj.optString("id", file.nameWithoutExtension), obj.optString("name", file.nameWithoutExtension), obj.optString("description", "Custom plugin"), obj.optString("command", ""), file.absolutePath)
            }.getOrNull()
        } ?: emptyList()
    }

    suspend fun exportWorkspaceBackup(destZip: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                fun walk(f: File, rel: String) {
                    if (f.isDirectory) f.listFiles()?.forEach { walk(it, if (rel.isEmpty()) it.name else "$rel/${it.name}") }
                    else { zos.putNextEntry(ZipEntry(rel)); FileInputStream(f).use { it.copyTo(zos) }; zos.closeEntry() }
                }
                walk(homeDir, "")
            }
            destZip
        }
    }

    suspend fun restoreWorkspaceBackup(srcZip: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            val canonicalHome = homeDir.canonicalFile
            ZipInputStream(FileInputStream(srcZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val out = File(homeDir, entry.name).canonicalFile
                    if (out.path.startsWith(canonicalHome.path) && !entry.isDirectory) {
                        out.parentFile?.mkdirs(); FileOutputStream(out).use { zis.copyTo(it) }; count++
                    }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
            count
        }
    }

    suspend fun askAiAssistant(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val key = settingsRepo.state.value.aiApiKey.trim()
        if (key.isEmpty()) return@withContext Result.failure(IllegalStateException("Provide your own API key in Settings to use the optional AI assistant."))
        val endpoint = settingsRepo.state.value.aiEndpoint.trim()
        runCatching {
            val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))).toString()
            val fullUrl = if (endpoint.contains("?")) "$endpoint&key=$key" else "$endpoint?key=$key"
            val req = Request.Builder().url(fullUrl).post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("API HTTP ${resp.code}: ${resp.body?.string()}")
                JSONObject(resp.body?.string() ?: "{}").getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }
        }
    }

    private fun startTaskSchedulerLoop() {
        schedulerJob?.cancel()
        schedulerJob = scope.launch {
            while (isActive) {
                delay(60_000L)
                val now = System.currentTimeMillis()
                for (task in runCatching { db.dao().getTasks() }.getOrDefault(emptyList())) {
                    if (task.enabled && now - task.lastRunAt >= task.intervalMinutes.coerceAtLeast(1) * 60_000L) {
                        db.dao().upsertTask(task.copy(lastRunAt = now, lastOutput = runShellSnippet(task.command)))
                    }
                }
            }
        }
    }

    suspend fun runScheduledTaskNow(task: AutomationTaskEntity): AutomationTaskEntity = withContext(Dispatchers.IO) {
        val updated = task.copy(lastRunAt = System.currentTimeMillis(), lastOutput = runShellSnippet(task.command))
        db.dao().upsertTask(updated); updated
    }

    private fun runShellSnippet(command: String): String = runCatching {
        val proc = ProcessBuilder("/system/bin/sh", "-c", command).directory(homeDir).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText(); proc.waitFor()
        out.take(1000).ifBlank { "(exit 0)" }
    }.getOrElse { it.message ?: "Error" }
}
