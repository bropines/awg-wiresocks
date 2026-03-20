package com.example.awgproxy

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import appctr.Appctr
import java.io.File

class AwgTileService : TileService() {

    // Вызывается, когда юзер открывает шторку (для обновления статуса иконки)
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = Appctr.isRunning()

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isRunning) "AWG Active" else "AWG Proxy"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) "Tap to stop" else "Tap to start"
        }
        
        tile.updateTile()
    }

    // Вызывается при клике на тайл
    override fun onClick() {
        super.onClick()
        val isRunning = Appctr.isRunning()
        val wgConfFile = File(filesDir, "awg.conf")

        if (isRunning) {
            startService(Intent(this, AwgService::class.java).apply { action = AwgService.ACTION_STOP })
        } else {
            // Если конфига нет, просто игнорим нажатие
            if (!wgConfFile.exists()) {
                updateTileState()
                return
            }

            val prefs = getSharedPreferences("awg_prefs", Context.MODE_PRIVATE)
            val socksPort = prefs.getString("socksPort", "1080") ?: "1080"
            val httpPort = prefs.getString("httpPort", "8080") ?: "8080"

            val finalConfig = """
                WGConfig = ${wgConfFile.absolutePath}
                
                [Socks5]
                BindAddress = 127.0.0.1:$socksPort
                
                [http]
                BindAddress = 127.0.0.1:$httpPort
            """.trimIndent()

            val intent = Intent(this, AwgService::class.java).apply {
                action = AwgService.ACTION_START
                putExtra(AwgService.EXTRA_CONFIG, finalConfig)
            }
            ContextCompat.startForegroundService(this, intent)
        }
        
        // Немного ждем, пока Go-ядро отработает, и обновляем внешний вид кнопки
        Thread {
            Thread.sleep(500)
            updateTileState()
        }.start()
    }
}