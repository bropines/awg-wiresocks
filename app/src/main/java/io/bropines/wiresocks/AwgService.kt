package io.bropines.wiresocks
import io.bropines.wiresocks.R

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import appctr.Appctr

class AwgService : Service() {

    private var lastConfigStr: String? = null
    
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_CONFIG = "EXTRA_CONFIG"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "wiresocks_proxy_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lastConfigStr = intent.getStringExtra(EXTRA_CONFIG) ?: ""
                startProxy(lastConfigStr!!)
            }
            ACTION_STOP -> stopProxy()
        }
        return START_STICKY
    }

    private fun startProxy(configStr: String) {
        val notification = buildNotification("Wiresocks is active")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Thread {
            try {
                Appctr.start(configStr, cacheDir.absolutePath)
            } catch (e: Exception) {
                Appctr.addLog("ERROR", "Core start failed: ${e.message}")
                stopProxy()
            }
        }.start()
    }

    private fun stopProxy() {
        Appctr.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setupNetworkMonitor() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (Appctr.isRunning() && lastConfigStr != null) {
                    Appctr.addLog("CORE", "Network changed. Reconnecting tunnel...")
                    Thread {
                        Appctr.stop()
                        Thread.sleep(1000)
                        Appctr.start(lastConfigStr!!, cacheDir.absolutePath)
                    }.start()
                }
            }
        }
        connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, AwgService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wiresocks Proxy")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Wiresocks Service", NotificationManager.IMPORTANCE_LOW).apply { description = "Background proxy engine status" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}