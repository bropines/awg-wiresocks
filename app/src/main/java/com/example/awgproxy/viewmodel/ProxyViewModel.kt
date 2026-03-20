package com.example.awgproxy.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import appctr.Appctr
import com.example.awgproxy.AwgService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ProxyViewModel(application: Application) : AndroidViewModel(application) {
    
    // Инициализируем хранилище и путь к файлу
    private val prefs = application.getSharedPreferences("awg_prefs", Context.MODE_PRIVATE)
    private val wgConfFile = File(application.filesDir, "awg.conf")

    // --- СОСТОЯНИЯ ДЛЯ UI ---
    private val _isRunning = MutableStateFlow(Appctr.isRunning())
    val isRunning = _isRunning.asStateFlow()

    private val _isConfigLoaded = MutableStateFlow(wgConfFile.exists())
    val isConfigLoaded = _isConfigLoaded.asStateFlow()

    private val _rawConfig = MutableStateFlow(if (wgConfFile.exists()) wgConfFile.readText() else "")
    val rawConfig = _rawConfig.asStateFlow()

    private val _socksPort = MutableStateFlow(prefs.getString("socksPort", "1080") ?: "1080")
    val socksPort = _socksPort.asStateFlow()

    private val _httpPort = MutableStateFlow(prefs.getString("httpPort", "8080") ?: "8080")
    val httpPort = _httpPort.asStateFlow()

    // Фоновая проверка статуса ядра (раз в секунду)
    init {
        viewModelScope.launch {
            while (true) {
                _isRunning.value = Appctr.isRunning()
                delay(1000)
            }
        }
    }

    // --- ФУНКЦИИ УПРАВЛЕНИЯ ---
    
    fun updateSocksPort(port: String) {
        val cleanPort = port.filter { it.isDigit() }
        _socksPort.value = cleanPort
        prefs.edit().putString("socksPort", cleanPort).apply()
    }

    fun updateHttpPort(port: String) {
        val cleanPort = port.filter { it.isDigit() }
        _httpPort.value = cleanPort
        prefs.edit().putString("httpPort", cleanPort).apply()
    }

    fun saveConfig(rawContent: String) {
            var fixed = rawContent.replace(Regex("(?m)^\\s*MTU\\s*=.*$"), "")
            if (fixed.contains("[Interface]")) {
                fixed = fixed.replaceFirst("[Interface]", "[Interface]\nMTU = 1280")
            }
            wgConfFile.writeText(fixed)
            _rawConfig.value = fixed // Обновляем текст в UI
            _isConfigLoaded.value = true
    }

    fun toggleProxy(context: Context) {
        if (_isRunning.value) {
            context.startService(Intent(context, AwgService::class.java).apply { action = AwgService.ACTION_STOP })
        } else {
            if (!_isConfigLoaded.value) return // Защита от старта без конфига

            // Собираем микро-конфиг для движка
            val finalConfig = """
                WGConfig = ${wgConfFile.absolutePath}
                
                [Socks5]
                BindAddress = 127.0.0.1:${_socksPort.value}
                
                [http]
                BindAddress = 127.0.0.1:${_httpPort.value}
            """.trimIndent()

            val intent = Intent(context, AwgService::class.java).apply {
                action = AwgService.ACTION_START
                putExtra(AwgService.EXTRA_CONFIG, finalConfig)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
    
    fun clearLogs() {
        Appctr.clearLogs()
    }
}