package com.developer.terminal.terminal

import android.content.Context
import com.developer.terminal.data.db.KestrelDatabase
import com.developer.terminal.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

object TerminalSessionRegistry {
    private val activePids = mutableSetOf<String>()

    @Synchronized fun register(id: String) { activePids.add(id) }
    @Synchronized fun unregister(id: String) { activePids.remove(id) }
    @Synchronized fun activeSessionCount(): Int = activePids.size
}

class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val workingDir: String,
    val shellPath: String,
    maxScrollback: Int,
    private val onRevision: () -> Unit,
    private val onExited: (Int) -> Unit
) {
    val emulator = TerminalEmulator(initialRows = 28, initialCols = 80, maxScrollback = maxScrollback)
    var ptyProcess: PtyProcess? = null
        private set
    var exitCode: Int? = null
        private set
    var isRunning: Boolean = false
        private set
    private var readJob: Job? = null

    fun start(scope: CoroutineScope, envArray: Array<String>, startupCommand: String = "") {
        if (isRunning) return
        val process = PtyProcess.start(shellPath, workingDir, arrayOf(shellPath, "-i"), envArray, emulator.rows, emulator.cols)
        if (process == null) {
            val err = "\r\n[Kestrel] Failed to spawn PTY for $shellPath\r\n"
            emulator.appendBytes(err.toByteArray(), err.length)
            onRevision()
            onExited(127)
            return
        }
        ptyProcess = process
        isRunning = true
        TerminalSessionRegistry.register(id)

        readJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(8192)
            while (isActive && process.isAlive) {
                val n = process.read(buf, 50)
                if (n > 0) { emulator.appendBytes(buf, n); onRevision() }
                else if (n < -1) break
            }
            val code = process.waitForExit()
            isRunning = false
            exitCode = code
            TerminalSessionRegistry.unregister(id)
            val footer = "\r\n[Process exited with code $code]\r\n"
            emulator.appendBytes(footer.toByteArray(), footer.length)
            onRevision()
            onExited(code)
        }
        if (startupCommand.isNotBlank()) sendText("$startupCommand\n")
    }

    fun sendText(text: String) { ptyProcess?.write(text.toByteArray(Charsets.UTF_8)) }
    fun sendBytes(bytes: ByteArray) { ptyProcess?.write(bytes) }
    fun resize(rows: Int, cols: Int) { emulator.resize(rows, cols); ptyProcess?.resize(rows, cols); onRevision() }
    fun terminate() { readJob?.cancel(); ptyProcess?.destroy(15); isRunning = false; TerminalSessionRegistry.unregister(id) }
}

class TerminalSessionManager(
    private val context: Context,
    private val db: KestrelDatabase,
    private val settingsRepo: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()
    private val _splitSessionId = MutableStateFlow<String?>(null)
    val splitSessionId: StateFlow<String?> = _splitSessionId.asStateFlow()
    private val _splitHorizontal = MutableStateFlow(false)
    val splitHorizontal: StateFlow<Boolean> = _splitHorizontal.asStateFlow()
    private val _renderTick = MutableStateFlow(0L)
    val renderTick: StateFlow<Long> = _renderTick.asStateFlow()
    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    val homeDir: File = File(context.filesDir, "home").apply { mkdirs() }
    val prefixDir: File = File(context.filesDir, "usr").apply { mkdirs() }
    val binDir: File = File(prefixDir, "bin").apply { mkdirs() }
    val etcDir: File = File(prefixDir, "etc").apply { mkdirs() }
    val tmpDir: File = File(prefixDir, "tmp").apply { mkdirs() }
    private val historyFile = File(homeDir, ".kestrel_history")

    init { loadHistory(); ensureBootstrapFiles() }

    private fun ensureBootstrapFiles() {
        val rcFile = File(etcDir, "kestrel.rc")
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val utilsBin = File(nativeDir, "libkestrel_utils.so").absolutePath
        rcFile.writeText(
            """
            export HOME="${homeDir.absolutePath}"
            export PREFIX="${prefixDir.absolutePath}"
            export TMPDIR="${tmpDir.absolutePath}"
            export TERM="xterm-256color"
            export KESTREL_NATIVE_UTILS="$utilsBin"
            export PATH="${binDir.absolutePath}:/system/bin:/system/xbin:/vendor/bin"
            alias kestrel-sysinfo='"$utilsBin" sysinfo'
            alias tree='"$utilsBin" tree'
            alias kwhich='"$utilsBin" which'
            alias kwc='"$utilsBin" wc'
            alias ll='ls -la'
            alias cls='clear'
            if [ -f "${homeDir.absolutePath}/.kestrelrc" ]; then
              . "${homeDir.absolutePath}/.kestrelrc"
            fi
            """.trimIndent() + "\n"
        )
        val userRc = File(homeDir, ".kestrelrc")
        if (!userRc.exists()) {
            userRc.writeText("# Kestrel user startup script (~/.kestrelrc)\nPS1='\\[\\e[33m\\]kestrel\\[\\e[0m\\]:\\[\\e[36m\\]\\w\\[\\e[0m\\]\\$ '\n")
        }
    }

    private fun loadHistory() {
        if (historyFile.exists()) {
            _commandHistory.value = runCatching { historyFile.readLines() }.getOrDefault(emptyList()).filter { it.isNotBlank() }.takeLast(200)
        }
    }

    fun recordCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        val next = (_commandHistory.value.filterNot { it == trimmed } + trimmed).takeLast(200)
        _commandHistory.value = next
        scope.launch(Dispatchers.IO) { runCatching { historyFile.writeText(next.joinToString("\n") + "\n") } }
    }

    fun createSession(title: String = "Shell ${_sessions.value.size + 1}", workingDir: String = homeDir.absolutePath, preferredShell: String? = null, startupCommand: String = ""): TerminalSession {
        val shell = resolveShell(preferredShell)
        val session = TerminalSession(title = title, workingDir = workingDir, shellPath = shell, maxScrollback = settingsRepo.state.value.scrollbackLines, onRevision = { _renderTick.value++ }, onExited = { _renderTick.value++ })
        _sessions.value = _sessions.value + session
        _activeSessionId.value = session.id
        if (settingsRepo.state.value.backgroundServiceEnabled) runCatching { TerminalForegroundService.ensureRunning(context) }

        scope.launch(Dispatchers.IO) {
            val envList = buildEnvironment()
            val aliasBlock = db.dao().getAliases().joinToString("; ") { "alias ${it.name}='${it.command.replace("'", "'\\''")}'" }
            val initCmd = buildString {
                if (aliasBlock.isNotBlank()) { append(aliasBlock); append("; ") }
                if (startupCommand.isNotBlank()) append(startupCommand)
            }
            session.start(scope, envList, initCmd)
        }
        return session
    }

    private fun resolveShell(preferred: String?): String {
        val candidates = listOfNotNull(preferred, File(binDir, "zsh").takeIf { it.exists() && it.canExecute() }?.absolutePath, File(binDir, "bash").takeIf { it.exists() && it.canExecute() }?.absolutePath, "/system/bin/sh")
        return candidates.firstOrNull { File(it).exists() } ?: "/system/bin/sh"
    }

    private suspend fun buildEnvironment(): Array<String> {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val map = linkedMapOf(
            "HOME" to homeDir.absolutePath, "PREFIX" to prefixDir.absolutePath, "TMPDIR" to tmpDir.absolutePath,
            "TERM" to "xterm-256color", "COLORTERM" to "truecolor", "LANG" to "en_US.UTF-8",
            "ENV" to File(etcDir, "kestrel.rc").absolutePath,
            "PATH" to "${binDir.absolutePath}:/system/bin:/system/xbin:/vendor/bin",
            "LD_LIBRARY_PATH" to "${prefixDir.absolutePath}/lib:$nativeDir:/system/lib64",
            "KESTREL_NATIVE_UTILS" to File(nativeDir, "libkestrel_utils.so").absolutePath
        )
        for (v in db.dao().getEnvVars()) if (v.key.isNotBlank()) map[v.key] = v.value
        return map.entries.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    fun selectSession(id: String) { if (_sessions.value.any { it.id == id }) _activeSessionId.value = id }
    fun closeSession(id: String) {
        val target = _sessions.value.firstOrNull { it.id == id } ?: return
        target.terminate()
        val remaining = _sessions.value.filterNot { it.id == id }
        _sessions.value = remaining
        if (_splitSessionId.value == id) _splitSessionId.value = null
        if (_activeSessionId.value == id) _activeSessionId.value = remaining.firstOrNull()?.id
    }
    fun toggleSplitPane() {
        val active = _activeSessionId.value
        if (_splitSessionId.value != null) { _splitSessionId.value = null; return }
        val other = _sessions.value.firstOrNull { it.id != active } ?: createSession("Split Shell")
        if (other.id != active) { _splitSessionId.value = other.id; if (active != null) _activeSessionId.value = active }
    }
    fun toggleSplitOrientation() { _splitHorizontal.value = !_splitHorizontal.value }
    fun runCommandInActiveSession(command: String) {
        val session = _sessions.value.firstOrNull { it.id == _activeSessionId.value } ?: createSession()
        recordCommand(command)
        session.sendText("$command\n")
    }
    fun autocompleteSuggestions(prefix: String): List<String> {
        val token = prefix.trim().split(Regex("\\s+")).lastOrNull().orEmpty()
        if (token.isEmpty()) return listOf("ls -la", "pwd", "kestrel-sysinfo", "tree", "df -h", "ps")
        val builtins = listOf("ls", "cd", "pwd", "mkdir", "rm", "cp", "mv", "cat", "grep", "find", "chmod", "vi", "sh", "ping", "ip", "df", "free", "ps", "uname", "env", "kestrel-sysinfo", "tree", "kwhich", "kwc", "clear", "exit")
        val cmdMatches = builtins.filter { it.startsWith(token) }
        val fileMatches = homeDir.listFiles()?.map { if (it.isDirectory) "${it.name}/" else it.name }?.filter { it.startsWith(token) }.orEmpty()
        val histMatches = _commandHistory.value.reversed().filter { it.startsWith(prefix.trim()) }.take(5)
        return (histMatches + cmdMatches + fileMatches).distinct().take(8)
    }
}
