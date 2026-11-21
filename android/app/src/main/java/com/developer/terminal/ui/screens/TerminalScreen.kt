package com.developer.terminal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.developer.terminal.KestrelApplication
import com.developer.terminal.data.prefs.TerminalColorScheme
import com.developer.terminal.terminal.TerminalCell
import com.developer.terminal.terminal.TerminalSession

@Composable
fun TerminalScreen(app: KestrelApplication) {
    val context = LocalContext.current
    val manager = app.terminalManager
    val sessions by manager.sessions.collectAsState()
    val activeId by manager.activeSessionId.collectAsState()
    val splitId by manager.splitSessionId.collectAsState()
    val splitHorizontal by manager.splitHorizontal.collectAsState()
    val tick by manager.renderTick.collectAsState()
    val settings by app.settings.state.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var ctrlArmed by remember { mutableStateOf(false) }
    var commandLineInput by remember { mutableStateOf("") }

    LaunchedEffect(sessions.isEmpty()) {
        if (sessions.isEmpty()) {
            manager.createSession("Shell 1")
        }
    }

    val activeSession = sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()
    val splitSession = sessions.firstOrNull { it.id == splitId }
    val suggestions = remember(commandLineInput) { manager.autocompleteSuggestions(commandLineInput) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Session Tab Strip + Split / Search / Clipboard Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sessions.forEach { session ->
                        val isSelected = session.id == activeSession?.id
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable { manager.selectSession(session.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (sessions.size > 1) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close tab",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { manager.closeSession(session.id) }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { manager.createSession() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = "New session", modifier = Modifier.size(18.dp))
                    }
                }

                IconButton(onClick = { showSearch = !showSearch }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search output", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { manager.toggleSplitPane() }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Splitscreen,
                        contentDescription = "Toggle split pane",
                        tint = if (splitSession != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (splitSession != null) {
                    TextButton(onClick = { manager.toggleSplitOrientation() }) {
                        Text(if (splitHorizontal) "H-Split" else "V-Split", style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(
                    onClick = {
                        val text = activeSession?.emulator?.fullTextSnapshot().orEmpty()
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Kestrel Terminal", text))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy output", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (text.isNotEmpty()) activeSession?.sendText(text)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                }
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Highlight matches in scrollback...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Terminal Viewport(s)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (activeSession != null) {
                if (splitSession != null) {
                    if (splitHorizontal) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                TerminalViewportPane(activeSession, settings.terminalScheme, settings.terminalFontSizeSp, tick, searchQuery)
                            }
                            Spacer(modifier = Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                TerminalViewportPane(splitSession, settings.terminalScheme, settings.terminalFontSizeSp, tick, searchQuery)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                TerminalViewportPane(activeSession, settings.terminalScheme, settings.terminalFontSizeSp, tick, searchQuery)
                            }
                            Spacer(modifier = Modifier.height(2.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outline))
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                TerminalViewportPane(splitSession, settings.terminalScheme, settings.terminalFontSizeSp, tick, searchQuery)
                            }
                        }
                    }
                } else {
                    TerminalViewportPane(activeSession, settings.terminalScheme, settings.terminalFontSizeSp, tick, searchQuery)
                }
            }
        }

        // Autocomplete Strip + Command Input & Extra Keys Bar
        if (suggestions.isNotEmpty() && commandLineInput.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { sug ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clickable { commandLineInput = sug }
                    ) {
                        Text(
                            text = sug,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Direct interactive command bar + soft-keyboard key forwarder
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("$", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = commandLineInput,
                        onValueChange = { commandLineInput = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val cmd = commandLineInput
                                if (cmd.isNotEmpty()) {
                                    manager.recordCommand(cmd)
                                    activeSession?.sendText("$cmd\n")
                                    commandLineInput = ""
                                } else {
                                    activeSession?.sendText("\n")
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                val native = event.nativeKeyEvent
                                if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                                    when (native.keyCode) {
                                        AndroidKeyEvent.KEYCODE_DPAD_UP -> { activeSession?.sendText("\u001B[A"); true }
                                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { activeSession?.sendText("\u001B[B"); true }
                                        AndroidKeyEvent.KEYCODE_TAB -> { activeSession?.sendText("\t"); true }
                                        else -> false
                                    }
                                } else false
                            },
                        decorationBox = { inner ->
                            if (commandLineInput.isEmpty()) {
                                Text(
                                    "Type shell command and press Send...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                    TextButton(
                        onClick = {
                            val cmd = commandLineInput
                            if (cmd.isNotEmpty()) manager.recordCommand(cmd)
                            activeSession?.sendText("$cmd\n")
                            commandLineInput = ""
                        }
                    ) {
                        Text("EXEC", fontFamily = FontFamily.Monospace)
                    }
                }

                // Extra Keys Row (ESC, TAB, CTRL, ALT, Pipes, Arrows, Ctrl+C, Ctrl+D)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExtraKeyChip("ESC") { activeSession?.sendBytes(byteArrayOf(0x1B)) }
                    ExtraKeyChip("TAB") { activeSession?.sendText("\t") }
                    ExtraKeyChip(if (ctrlArmed) "CTRL*" else "CTRL", active = ctrlArmed) { ctrlArmed = !ctrlArmed }
                    ExtraKeyChip("CTRL+C") { activeSession?.ptyProcess?.sendInterrupt(); ctrlArmed = false }
                    ExtraKeyChip("CTRL+D") { activeSession?.sendBytes(byteArrayOf(4)) }
                    ExtraKeyChip("CTRL+L") { activeSession?.sendBytes(byteArrayOf(12)) }
                    ExtraKeyChip("|") { activeSession?.sendText("|") }
                    ExtraKeyChip("/") { activeSession?.sendText("/") }
                    ExtraKeyChip("-") { activeSession?.sendText("-") }
                    ExtraKeyChip("~") { activeSession?.sendText("~") }
                    ExtraKeyChip("UP") { activeSession?.sendText("\u001B[A") }
                    ExtraKeyChip("DOWN") { activeSession?.sendText("\u001B[B") }
                    ExtraKeyChip("LEFT") { activeSession?.sendText("\u001B[D") }
                    ExtraKeyChip("RIGHT") { activeSession?.sendText("\u001B[C") }
                }
            }
        }
    }
}

@Composable
private fun ExtraKeyChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun TerminalViewportPane(
    session: TerminalSession,
    scheme: TerminalColorScheme,
    fontSizeSp: Int,
    renderTick: Long,
    searchQuery: String
) {
    val bgColor = Color(scheme.bgHex)
    val fgColor = Color(scheme.fgHex)
    val highlightColor = Color(scheme.selectionHex)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(6.dp)
    ) {
        val approxCharW = with(density) { (fontSizeSp * 0.62f).sp.toPx() }.coerceAtLeast(8f)
        val approxLineH = with(density) { (fontSizeSp * 1.35f).sp.toPx() }.coerceAtLeast(14f)
        val calcCols = (constraints.maxWidth / approxCharW).toInt().coerceIn(32, 180)
        val calcRows = (constraints.maxHeight / approxLineH).toInt().coerceIn(10, 80)

        LaunchedEffect(calcRows, calcCols, session.id) {
            session.resize(calcRows, calcCols)
        }

        val (lines, cursorRow) = remember(renderTick, session.id) {
            session.emulator.snapshotLines()
        }
        val listState = rememberLazyListState()

        LaunchedEffect(lines.size, renderTick) {
            if (lines.isNotEmpty()) {
                listState.scrollToItem(lines.lastIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lines) { rowIdx, line ->
                val styled = remember(line, searchQuery, scheme, rowIdx == cursorRow) {
                    buildStyledTerminalLine(
                        cells = line.cells,
                        defaultFg = fgColor,
                        defaultBg = bgColor,
                        searchQuery = searchQuery,
                        highlightBg = highlightColor
                    )
                }
                Text(
                    text = styled,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.35f).sp,
                    softWrap = true
                )
            }
        }
    }
}

private fun buildStyledTerminalLine(
    cells: List<TerminalCell>,
    defaultFg: Color,
    defaultBg: Color,
    searchQuery: String,
    highlightBg: Color
): AnnotatedString {
    val rawText = buildString(cells.size) { for (c in cells) append(c.char) }.trimEnd()
    if (rawText.isEmpty()) return AnnotatedString(" ")
    return buildAnnotatedString {
        append(rawText)
        var i = 0
        while (i < rawText.length && i < cells.size) {
            val c = cells[i]
            val fg = if (c.inverse) (if (c.bgColor == TerminalCell.DEFAULT_BG) defaultBg else Color(c.bgColor))
            else (if (c.fgColor == TerminalCell.DEFAULT_FG) defaultFg else Color(c.fgColor))
            val bg = if (c.inverse) (if (c.fgColor == TerminalCell.DEFAULT_FG) defaultFg else Color(c.fgColor))
            else (if (c.bgColor == TerminalCell.DEFAULT_BG) Color.Transparent else Color(c.bgColor))

            var j = i + 1
            while (j < rawText.length && j < cells.size &&
                cells[j].fgColor == c.fgColor &&
                cells[j].bgColor == c.bgColor &&
                cells[j].bold == c.bold &&
                cells[j].italic == c.italic &&
                cells[j].underline == c.underline &&
                cells[j].inverse == c.inverse
            ) {
                j++
            }
            addStyle(
                SpanStyle(
                    color = fg,
                    background = bg,
                    fontWeight = if (c.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (c.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (c.underline) TextDecoration.Underline else TextDecoration.None
                ),
                start = i,
                end = j
            )
            i = j
        }
        if (searchQuery.isNotBlank()) {
            var idx = rawText.indexOf(searchQuery, ignoreCase = true)
            while (idx >= 0) {
                addStyle(SpanStyle(background = highlightBg, fontWeight = FontWeight.Bold), idx, idx + searchQuery.length)
                idx = rawText.indexOf(searchQuery, idx + searchQuery.length, ignoreCase = true)
            }
        }
    }
}
