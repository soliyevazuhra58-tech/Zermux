package com.developer.terminal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {
    @Test
    fun writesPlainTextAndWrapsLines() {
        val emulator = TerminalEmulator(initialRows = 10, initialCols = 40, maxScrollback = 100)
        val msg = "kestrel-pty-ready\r\nline-two"
        emulator.appendBytes(msg.toByteArray(), msg.length)
        val snapshot = emulator.fullTextSnapshot()
        assertTrue(snapshot.contains("kestrel-pty-ready"))
        assertTrue(snapshot.contains("line-two"))
        assertEquals(1, emulator.cursorRow)
    }

    @Test
    fun parsesAnsiSgrColorsAndAlternateScreen() {
        val emulator = TerminalEmulator(initialRows = 10, initialCols = 40, maxScrollback = 100)
        val seq = "\u001B[31mERROR\u001B[0m\u001B[?1049hALT\u001B[?1049l"
        emulator.appendBytes(seq.toByteArray(), seq.length)
        val (lines, _) = emulator.snapshotLines()
        assertEquals("ERROR", lines.first().text())
        assertTrue(lines.first().cells.first().fgColor != TerminalCell.DEFAULT_FG)
    }

    @Test
    fun resizeClampsCursorInsideViewport() {
        val emulator = TerminalEmulator(initialRows = 24, initialCols = 80, maxScrollback = 100)
        val move = "\u001B[20;70H@"
        emulator.appendBytes(move.toByteArray(), move.length)
        emulator.resize(12, 40)
        assertTrue(emulator.cursorRow < 12)
        assertTrue(emulator.cursorCol < 40)
    }
}
