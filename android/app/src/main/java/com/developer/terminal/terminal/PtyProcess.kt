package com.developer.terminal.terminal

import android.util.Log

class PtyProcess private constructor(
    val pid: Int,
    private var masterFd: Int
) {
    @Volatile
    var isAlive: Boolean = true
        private set

    fun resize(rows: Int, cols: Int) {
        if (masterFd >= 0 && isAlive) setPtyWindowSize(masterFd, rows, cols)
    }

    fun read(buffer: ByteArray, timeoutMs: Int = 50): Int {
        if (masterFd < 0) return -2
        return readFd(masterFd, buffer, timeoutMs)
    }

    fun write(bytes: ByteArray): Int {
        if (masterFd < 0 || !isAlive) return -1
        return writeFd(masterFd, bytes, bytes.size)
    }

    fun sendInterrupt() {
        write(byteArrayOf(3))
    }

    fun destroy(signal: Int = 15) {
        if (isAlive) {
            isAlive = false
            sendSignal(pid, signal)
            if (masterFd >= 0) {
                closeFd(masterFd)
                masterFd = -1
            }
        }
    }

    fun waitForExit(): Int {
        val code = waitFor(pid)
        isAlive = false
        if (masterFd >= 0) {
            closeFd(masterFd)
            masterFd = -1
        }
        return code
    }

    companion object {
        init {
            try {
                System.loadLibrary("terminal_pty")
            } catch (t: Throwable) {
                Log.e("KestrelPTY", "Failed to load native terminal_pty library", t)
            }
        }

        fun start(
            command: String,
            cwd: String,
            args: Array<String>,
            env: Array<String>,
            rows: Int,
            cols: Int
        ): PtyProcess? {
            val pair = createSubprocess(command, cwd, args, env, rows, cols) ?: return null
            if (pair.size < 2 || pair[0] <= 0 || pair[1] < 0) return null
            return PtyProcess(pid = pair[0], masterFd = pair[1])
        }

        @JvmStatic
        private external fun createSubprocess(cmd: String, cwd: String, args: Array<String>, env: Array<String>, rows: Int, cols: Int): IntArray?

        @JvmStatic
        private external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int): Int

        @JvmStatic
        private external fun readFd(fd: Int, buffer: ByteArray, timeoutMs: Int): Int

        @JvmStatic
        private external fun writeFd(fd: Int, data: ByteArray, length: Int): Int

        @JvmStatic
        private external fun closeFd(fd: Int)

        @JvmStatic
        private external fun waitFor(pid: Int): Int

        @JvmStatic
        private external fun sendSignal(pid: Int, sig: Int): Int
    }
}
