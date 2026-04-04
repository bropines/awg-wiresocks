package io.bropines.wiresocks

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import appctr.Appctr
import java.io.File

class AwgTileService : TileService() {

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

    override fun onClick() {
        super.onClick()
        val isRunning = Appctr.isRunning()

        if (isRunning) {
            startService(Intent(this, AwgService::class.java).apply { action = AwgService.ACTION_STOP })
        } else {
            val prefs = getSharedPreferences("awg_prefs", Context.MODE_PRIVATE)
            val selectedConfName = prefs.getString("selectedConfig", null)
            
            if (selectedConfName == null) {
                updateTileState()
                return
            }
            
            val wgConfFile = File(File(filesDir, "configs"), selectedConfName)
            if (!wgConfFile.exists()) {
                updateTileState()
                return
            }

            // Передаем текст конфига целиком для поддержки UDP туннелей
            val fileContent = wgConfFile.readText()
            val socksPort = prefs.getString("socksPort", "1080") ?: "1080"
            val httpPort = prefs.getString("httpPort", "8080") ?: "8080"

            val finalConfig = """
                $fileContent
                
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
        
        Thread {
            Thread.sleep(500)
            updateTileState()
        }.start()
    }
}