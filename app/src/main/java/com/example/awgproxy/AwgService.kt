package com.example.awgproxy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import appctr.Appctr

class AwgService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_CONFIG = "EXTRA_CONFIG"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "awg_proxy_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val configStr = intent.getStringExtra(EXTRA_CONFIG) ?: ""
                startProxy(configStr)
            }
            ACTION_STOP -> stopProxy()
        }
        return START_STICKY
    }

    private fun startProxy(configStr: String) {
        // Показываем уведомление (обязательно для Foreground Service)
        startForeground(NOTIFICATION_ID, buildNotification("AWG Proxy is running"))

        Thread {
            try {
                if (!Appctr.isRunning()) {
                    // Передаем конфиг и кэш-директорию в наш Go-код
                    Appctr.start(configStr, cacheDir.absolutePath)
                }
            } catch (e: Exception) {
                Appctr.addLog("ERROR", "Service crash: ${e.message}")
                stopProxy()
            }
        }.start()
    }

    private fun stopProxy() {
        Appctr.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AwgService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AWG Proxy")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_secure) // Встроенная иконка замочка
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AWG Proxy Service", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}