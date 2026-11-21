package com.developer.terminal.terminal

import kotlin.math.max
import kotlin.math.min

data class TerminalCell(
    val char: Char = ' ',
    val fgColor: Int = DEFAULT_FG,
    val bgColor: Int = DEFAULT_BG,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
) {
    companion object {
        const val DEFAULT_FG = -1
        const val DEFAULT_BG = -2
    }
}

data class TerminalLine(val cells: ArrayList<TerminalCell>) {
    fun text(): String = buildString(cells.size) { for (c in cells) append(c.char) }.trimEnd()
}

class TerminalEmulator(
    initialRows: Int = 28,
    initialCols: Int = 80,
    private val maxScrollback: Int = 3000
) {
    var rows: Int = initialRows
        private set
    var cols: Int = initialCols
        private set
    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set
    var cursorVisible: Boolean = true
        private set

    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var curFg = TerminalCell.DEFAULT_FG
    private var curBg = TerminalCell.DEFAULT_BG
    private var curBold = false
    private var curItalic = false
    private var curUnderline = false
    private var curInverse = false

    private val primaryScrollback = ArrayDeque<TerminalLine>()
    private var primaryScreen = ArrayList<ArrayList<TerminalCell>>()
    private var altScreen = ArrayList<ArrayList<TerminalCell>>()
    private var useAltScreen = false

    private enum class ParseState { GROUND, ESCAPE, CSI, OSC, CHARSET }
    private var parseState = ParseState.GROUND
    private val csiBuffer = StringBuilder()
    private val oscBuffer = StringBuilder()

    private var utf8Bytes = ByteArray(4)
    private var utf8Needed = 0
    private var utf8Len = 0

    @Volatile
    var revision: Long = 0L
        private set

    init {
        initScreen(primaryScreen, rows, cols)
        initScreen(altScreen, rows, cols)
    }

    private fun initScreen(screen: ArrayList<ArrayList<TerminalCell>>, r: Int, c: Int) {
        screen.clear()
        repeat(r) {
            val rowList = ArrayList<TerminalCell>(c)
            repeat(c) { rowList.add(TerminalCell()) }
            screen.add(rowList)
        }
    }

    private fun activeScreen(): ArrayList<ArrayList<TerminalCell>> =
        if (useAltScreen) altScreen else primaryScreen

    @Synchronized
    fun resize(newRows: Int, newCols: Int) {
        val r = max(4, newRows)
        val c = max(20, newCols)
        if (r == rows && c == cols) return
        resizeBuffer(primaryScreen, r, c)
        resizeBuffer(altScreen, r, c)
        rows = r
        cols = c
        cursorRow = min(cursorRow, rows - 1)
        cursorCol = min(cursorCol, cols - 1)
        revision++
    }

    private fun resizeBuffer(buf: ArrayList<ArrayList<TerminalCell>>, r: Int, c: Int) {
        while (buf.size < r) {
            val row = ArrayList<TerminalCell>(c)
            repeat(c) { row.add(TerminalCell()) }
            buf.add(row)
        }
        while (buf.size > r) {
            if (buf === primaryScreen && buf.isNotEmpty()) {
                pushScrollback(TerminalLine(buf.removeAt(0)))
                if (cursorRow > 0) cursorRow--
            } else {
                buf.removeAt(buf.lastIndex)
            }
        }
        for (row in buf) {
            while (row.size < c) row.add(TerminalCell())
            while (row.size > c) row.removeAt(row.lastIndex)
        }
    }

    @Synchronized
    fun appendBytes(bytes: ByteArray, length: Int) {
        for (i in 0 until length) {
            val b = bytes[i].toInt() and 0xFF
            if (utf8Needed > 0) {
                if ((b and 0xC0) == 0x80) {
                    utf8Bytes[utf8Len++] = bytes[i]
                    if (utf8Len == utf8Needed) {
                        val decoded = String(utf8Bytes, 0, utf8Len, Charsets.UTF_8)
                        for (ch in decoded) processChar(ch)
                        utf8Needed = 0
                        utf8Len = 0
                    }
                    continue
                } else {
                    utf8Needed = 0
                    utf8Len = 0
                }
            }
            when {
                b < 0x80 -> processChar(b.toChar())
                (b and 0xE0) == 0xC0 -> { utf8Bytes[0] = bytes[i]; utf8Len = 1; utf8Needed = 2 }
                (b and 0xF0) == 0xE0 -> { utf8Bytes[0] = bytes[i]; utf8Len = 1; utf8Needed = 3 }
                (b and 0xF8) == 0xF0 -> { utf8Bytes[0] = bytes[i]; utf8Len = 1; utf8Needed = 4 }
                else -> processChar('\uFFFD')
            }
        }
        revision++
    }

    @Synchronized
    fun clearAll() {
        primaryScrollback.clear()
        initScreen(primaryScreen, rows, cols)
        initScreen(altScreen, rows, cols)
        cursorRow = 0
        cursorCol = 0
        revision++
    }

    private fun processChar(ch: Char) {
        when (parseState) {
            ParseState.GROUND -> handleGroundChar(ch)
            ParseState.ESCAPE -> handleEscapeChar(ch)
            ParseState.CSI -> handleCsiChar(ch)
            ParseState.OSC -> handleOscChar(ch)
            ParseState.CHARSET -> parseState = ParseState.GROUND
        }
    }

    private fun handleGroundChar(ch: Char) {
        when (ch) {
            '\u001B' -> parseState = ParseState.ESCAPE
            '\r' -> cursorCol = 0
            '\n', '\u000B', '\u000C' -> lineFeed()
            '\b' -> if (cursorCol > 0) cursorCol--
            '\t' -> cursorCol = min(((cursorCol / 8) + 1) * 8, cols - 1)
            '\u0007' -> {}
            else -> if (ch.code >= 32) putChar(ch)
        }
    }

    private fun handleEscapeChar(ch: Char) {
        when (ch) {
            '[' -> { csiBuffer.clear(); parseState = ParseState.CSI }
            ']' -> { oscBuffer.clear(); parseState = ParseState.OSC }
            '(', ')', '*', '+' -> parseState = ParseState.CHARSET
            '7' -> { savedCursorRow = cursorRow; savedCursorCol = cursorCol; parseState = ParseState.GROUND }
            '8' -> { cursorRow = min(savedCursorRow, rows - 1); cursorCol = min(savedCursorCol, cols - 1); parseState = ParseState.GROUND }
            'M' -> { if (cursorRow == 0) scrollDownOne() else cursorRow--; parseState = ParseState.GROUND }
            'c' -> { resetAttributes(); initScreen(activeScreen(), rows, cols); cursorRow = 0; cursorCol = 0; parseState = ParseState.GROUND }
            else -> parseState = ParseState.GROUND
        }
    }

    private fun handleOscChar(ch: Char) {
        if (ch == '\u0007' || (ch == '\\' && oscBuffer.endsWith('\u001B')) || ch == '\n' || ch == '\r') {
            parseState = ParseState.GROUND
        } else if (oscBuffer.length < 512) {
            oscBuffer.append(ch)
        }
    }

    private fun handleCsiChar(ch: Char) {
        if (ch.code in 0x40..0x7E) {
            executeCsi(ch, csiBuffer.toString())
            parseState = ParseState.GROUND
        } else if (csiBuffer.length < 128) {
            csiBuffer.append(ch)
        }
    }

    private fun executeCsi(cmd: Char, rawParams: String) {
        val isPrivate = rawParams.startsWith("?")
        val clean = if (isPrivate) rawParams.drop(1) else rawParams
        val params = if (clean.isEmpty()) emptyList() else clean.split(';').map { it.toIntOrNull() ?: 0 }
        fun p(index: Int, default: Int): Int {
            val v = params.getOrNull(index) ?: return default
            return if (v == 0) default else v
        }
        val screen = activeScreen()
        when (cmd) {
            'A' -> cursorRow = max(0, cursorRow - p(0, 1))
            'B' -> cursorRow = min(rows - 1, cursorRow + p(0, 1))
            'C' -> cursorCol = min(cols - 1, cursorCol + p(0, 1))
            'D' -> cursorCol = max(0, cursorCol - p(0, 1))
            'E' -> { cursorRow = min(rows - 1, cursorRow + p(0, 1)); cursorCol = 0 }
            'F' -> { cursorRow = max(0, cursorRow - p(0, 1)); cursorCol = 0 }
            'G' -> cursorCol = (p(0, 1) - 1).coerceIn(0, cols - 1)
            'H', 'f' -> { cursorRow = (p(0, 1) - 1).coerceIn(0, rows - 1); cursorCol = (p(1, 1) - 1).coerceIn(0, cols - 1) }
            'J' -> when (params.firstOrNull() ?: 0) {
                0 -> { clearRange(screen[cursorRow], cursorCol, cols); for (r in cursorRow + 1 until rows) clearRange(screen[r], 0, cols) }
                1 -> { for (r in 0 until cursorRow) clearRange(screen[r], 0, cols); clearRange(screen[cursorRow], 0, cursorCol + 1) }
                2, 3 -> for (r in 0 until rows) clearRange(screen[r], 0, cols)
            }
            'K' -> when (params.firstOrNull() ?: 0) {
                0 -> clearRange(screen[cursorRow], cursorCol, cols)
                1 -> clearRange(screen[cursorRow], 0, cursorCol + 1)
                2 -> clearRange(screen[cursorRow], 0, cols)
            }
            'L' -> repeat(p(0, 1)) {
                if (cursorRow < rows) {
                    screen.removeAt(rows - 1)
                    screen.add(cursorRow, ArrayList<TerminalCell>(cols).apply { repeat(cols) { add(TerminalCell()) } })
                }
            }
            'M' -> repeat(p(0, 1)) {
                if (cursorRow < rows) {
                    screen.removeAt(cursorRow)
                    screen.add(ArrayList<TerminalCell>(cols).apply { repeat(cols) { add(TerminalCell()) } })
                }
            }
            'P' -> repeat(p(0, 1)) {
                val row = screen[cursorRow]
                if (cursorCol < row.size) { row.removeAt(cursorCol); row.add(TerminalCell()) }
            }
            'm' -> handleSgr(params.ifEmpty { listOf(0) })
            's' -> { savedCursorRow = cursorRow; savedCursorCol = cursorCol }
            'u' -> { cursorRow = savedCursorRow.coerceIn(0, rows - 1); cursorCol = savedCursorCol.coerceIn(0, cols - 1) }
            'h' -> if (isPrivate) for (code in params) when (code) {
                25 -> cursorVisible = true
                1049, 47, 1047 -> { useAltScreen = true; initScreen(altScreen, rows, cols); cursorRow = 0; cursorCol = 0 }
            }
            'l' -> if (isPrivate) for (code in params) when (code) {
                25 -> cursorVisible = false
                1049, 47, 1047 -> useAltScreen = false
            }
        }
    }

    private fun handleSgr(params: List<Int>) {
        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> resetAttributes()
                1 -> curBold = true
                3 -> curItalic = true
                4 -> curUnderline = true
                7 -> curInverse = true
                22 -> curBold = false
                23 -> curItalic = false
                24 -> curUnderline = false
                27 -> curInverse = false
                in 30..37 -> curFg = ANSI_16[p - 30]
                38 -> {
                    val mode = params.getOrNull(i + 1)
                    if (mode == 5 && i + 2 < params.size) { curFg = xterm256Color(params[i + 2]); i += 2 }
                    else if (mode == 2 && i + 4 < params.size) {
                        curFg = (0xFF shl 24) or ((params[i + 2] and 0xFF) shl 16) or ((params[i + 3] and 0xFF) shl 8) or (params[i + 4] and 0xFF)
                        i += 4
                    }
                }
                39 -> curFg = TerminalCell.DEFAULT_FG
                in 40..47 -> curBg = ANSI_16[p - 40]
                48 -> {
                    val mode = params.getOrNull(i + 1)
                    if (mode == 5 && i + 2 < params.size) { curBg = xterm256Color(params[i + 2]); i += 2 }
                    else if (mode == 2 && i + 4 < params.size) {
                        curBg = (0xFF shl 24) or ((params[i + 2] and 0xFF) shl 16) or ((params[i + 3] and 0xFF) shl 8) or (params[i + 4] and 0xFF)
                        i += 4
                    }
                }
                49 -> curBg = TerminalCell.DEFAULT_BG
                in 90..97 -> curFg = ANSI_16[8 + (p - 90)]
                in 100..107 -> curBg = ANSI_16[8 + (p - 100)]
            }
            i++
        }
    }

    private fun resetAttributes() {
        curFg = TerminalCell.DEFAULT_FG; curBg = TerminalCell.DEFAULT_BG
        curBold = false; curItalic = false; curUnderline = false; curInverse = false
    }

    private fun clearRange(row: ArrayList<TerminalCell>, from: Int, to: Int) {
        for (c in max(0, from) until min(row.size, to)) row[c] = TerminalCell()
    }

    private fun putChar(ch: Char) {
        if (cursorCol >= cols) { cursorCol = 0; lineFeed() }
        activeScreen()[cursorRow][cursorCol] = TerminalCell(ch, curFg, curBg, curBold, curItalic, curUnderline, curInverse)
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow < rows - 1) cursorRow++
        else {
            val screen = activeScreen()
            val top = screen.removeAt(0)
            if (!useAltScreen) pushScrollback(TerminalLine(top))
            screen.add(ArrayList<TerminalCell>(cols).apply { repeat(cols) { add(TerminalCell()) } })
        }
    }

    private fun scrollDownOne() {
        val screen = activeScreen()
        screen.removeAt(rows - 1)
        screen.add(0, ArrayList<TerminalCell>(cols).apply { repeat(cols) { add(TerminalCell()) } })
    }

    private fun pushScrollback(line: TerminalLine) {
        if (maxScrollback <= 0) return
        if (primaryScrollback.size >= maxScrollback) primaryScrollback.removeFirst()
        primaryScrollback.addLast(line)
    }

    @Synchronized
    fun snapshotLines(): Pair<List<TerminalLine>, Int> {
        val result = ArrayList<TerminalLine>(primaryScrollback.size + rows)
        val cursorOffset: Int
        if (!useAltScreen) {
            result.addAll(primaryScrollback)
            cursorOffset = primaryScrollback.size + cursorRow
            for (r in primaryScreen) result.add(TerminalLine(ArrayList(r)))
        } else {
            cursorOffset = cursorRow
            for (r in altScreen) result.add(TerminalLine(ArrayList(r)))
        }
        return result to cursorOffset
    }

    @Synchronized
    fun fullTextSnapshot(): String = snapshotLines().first.joinToString("\n") { it.text() }.trimEnd()

    companion object {
        private val ANSI_16 = intArrayOf(
            0xFF1F242D.toInt(), 0xFFEF4444.toInt(), 0xFF22C55E.toInt(), 0xFFEAB308.toInt(),
            0xFF3B82F6.toInt(), 0xFFA855F7.toInt(), 0xFF06B6D4.toInt(), 0xFFD1D5DB.toInt(),
            0xFF6B7280.toInt(), 0xFFF87171.toInt(), 0xFF4ADE80.toInt(), 0xFFFACC15.toInt(),
            0xFF60A5FA.toInt(), 0xFFC084FC.toInt(), 0xFF22D3EE.toInt(), 0xFFF9FAFB.toInt()
        )

        private fun xterm256Color(idx: Int): Int {
            val i = idx.coerceIn(0, 255)
            if (i < 16) return ANSI_16[i]
            if (i < 232) {
                val c = i - 16
                val r = (c / 36).let { if (it == 0) 0 else 55 + it * 40 }
                val g = ((c % 36) / 6).let { if (it == 0) 0 else 55 + it * 40 }
                val b = (c % 6).let { if (it == 0) 0 else 55 + it * 40 }
                return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val gray = 8 + (i - 232) * 10
            return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
    }
}
