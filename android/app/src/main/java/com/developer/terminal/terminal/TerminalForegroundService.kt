package com.developer.terminal.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.developer.terminal.MainActivity
import com.developer.terminal.R

class TerminalForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ACQUIRE_WAKELOCK -> acquireWakeLock()
            ACTION_RELEASE_WAKELOCK -> releaseWakeLock()
        }
        startForeground(NOTIFICATION_ID, buildNotification(TerminalSessionRegistry.activeSessionCount()))
        return START_STICKY
    }

    private fun buildNotification(sessionCount: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("route", "terminal")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, TerminalForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (sessionCount > 0) "$sessionCount active shell session${if (sessionCount == 1) "" else "s"} running" else "Kestrel background workspace ready"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kestrel Terminal")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(0, "Stop Service", stopPending)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kestrel::TerminalWakeLock").apply { acquire(60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Terminal Sessions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps active terminal shells and local servers alive"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { releaseWakeLock(); super.onDestroy() }

    companion object {
        const val CHANNEL_ID = "kestrel_terminal_sessions"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.developer.terminal.ACTION_STOP_SERVICE"
        const val ACTION_ACQUIRE_WAKELOCK = "com.developer.terminal.ACTION_ACQUIRE_WAKELOCK"
        const val ACTION_RELEASE_WAKELOCK = "com.developer.terminal.ACTION_RELEASE_WAKELOCK"

        fun ensureRunning(context: Context) {
            val intent = Intent(context, TerminalForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TerminalForegroundService::class.java).apply { action = ACTION_STOP })
        }
    }
}
