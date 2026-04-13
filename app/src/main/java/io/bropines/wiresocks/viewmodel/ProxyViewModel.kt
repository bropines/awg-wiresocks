package io.bropines.wiresocks.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import appctr.Appctr
import io.bropines.wiresocks.AwgService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("awg_prefs", Context.MODE_PRIVATE)
    private val configsDir = File(application.filesDir, "configs").apply { mkdirs() }

    private val _isRunning = MutableStateFlow(Appctr.isRunning())
    val isRunning = _isRunning.asStateFlow()

    private val _availableConfigs = MutableStateFlow<List<File>>(emptyList())
    val availableConfigs = _availableConfigs.asStateFlow()

    private val _selectedConfig = MutableStateFlow<File?>(null)
    val selectedConfig = _selectedConfig.asStateFlow()

    private val _rawConfig = MutableStateFlow("")
    val rawConfig = _rawConfig.asStateFlow()

    private val _socksPort = MutableStateFlow(prefs.getString("socksPort", "48151") ?: "48151")
    val socksPort = _socksPort.asStateFlow()

    private val _httpPort = MutableStateFlow(prefs.getString("httpPort", "") ?: "")
    val httpPort = _httpPort.asStateFlow()

    private val _socksUser = MutableStateFlow(prefs.getString("socksUser", "") ?: "")
    val socksUser = _socksUser.asStateFlow()

    private val _socksPass = MutableStateFlow(prefs.getString("socksPass", "") ?: "")
    val socksPass = _socksPass.asStateFlow()

    // НОВЫЙ СТЕЙТ ДЛЯ ОТКЛЮЧЕНИЯ UDP
    private val _disableUdp = MutableStateFlow(prefs.getBoolean("disableUdp", false))
    val disableUdp = _disableUdp.asStateFlow()

    private val _pingHost = MutableStateFlow(prefs.getString("pingHost", "1.1.1.1") ?: "1.1.1.1")
    val pingHost = _pingHost.asStateFlow()

    private val _pingResult = MutableStateFlow<String?>(null)
    val pingResult = _pingResult.asStateFlow()

    init {
        loadConfigsList()
        val savedConfName = prefs.getString("selectedConfig", null)
        if (savedConfName != null && File(configsDir, savedConfName).exists()) {
            selectConfig(File(configsDir, savedConfName))
        } else {
            _availableConfigs.value.firstOrNull()?.let { selectConfig(it) }
        }

        viewModelScope.launch {
            while (true) {
                _isRunning.value = Appctr.isRunning()
                delay(3000)
            }
        }
    }

    private fun loadConfigsList() {
        _availableConfigs.value = configsDir.listFiles()?.toList()?.sortedBy { it.name } ?: emptyList()
    }

    fun selectConfig(file: File) {
        _selectedConfig.value = file
        _rawConfig.value = file.readText()
        prefs.edit().putString("selectedConfig", file.name).apply()
    }

    fun saveCurrentConfig(rawContent: String) {
        _selectedConfig.value?.let { file ->
            file.writeText(rawContent)
            _rawConfig.value = rawContent
        }
    }

    fun importConfig(context: Context, uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = InputStreamReader(inputStream).readText()
                var fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "profile_${System.currentTimeMillis()}.conf"
                if (!fileName.endsWith(".conf")) fileName += ".conf"
                
                val newFile = File(configsDir, fileName)
                newFile.writeText(content)
                loadConfigsList()
                selectConfig(newFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun importConfigFromString(name: String, content: String) {
        val newFile = File(configsDir, if (name.endsWith(".conf")) name else "$name.conf")
        newFile.writeText(content)
        loadConfigsList()
        selectConfig(newFile)
    }

    fun deleteSelectedConfig() {
        _selectedConfig.value?.let { file ->
            if (file.exists()) file.delete()
            loadConfigsList()
            _availableConfigs.value.firstOrNull()?.let { selectConfig(it) } ?: run {
                _selectedConfig.value = null
                _rawConfig.value = ""
                prefs.edit().remove("selectedConfig").apply()
            }
        }
    }

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

    fun updateSocksUser(user: String) {
        _socksUser.value = user.trim()
        prefs.edit().putString("socksUser", user.trim()).apply()
    }

    fun updateSocksPass(pass: String) {
        _socksPass.value = pass.trim()
        prefs.edit().putString("socksPass", pass.trim()).apply()
    }

    // НОВАЯ ФУНКЦИЯ ОБНОВЛЕНИЯ UDP ФЛАГА
    fun updateDisableUdp(disable: Boolean) {
        _disableUdp.value = disable
        prefs.edit().putBoolean("disableUdp", disable).apply()
    }

    fun updatePingHost(host: String) {
        _pingHost.value = host
        prefs.edit().putString("pingHost", host).apply()
    }

    fun toggleProxy(context: Context) {
        if (_isRunning.value) {
            context.startService(Intent(context, AwgService::class.java).apply { action = AwgService.ACTION_STOP })
        } else {
            val currentFile = _selectedConfig.value ?: return
            
            val fileContent = currentFile.readText()

            val authConfig = if (_socksUser.value.isNotBlank() && _socksPass.value.isNotBlank()) {
                "\nUsername = ${_socksUser.value}\nPassword = ${_socksPass.value}"
            } else ""

            // ПОДКИДЫВАЕМ ФЛАГ ОТКЛЮЧЕНИЯ UDP В КОНФИГ
            val udpConfig = if (_disableUdp.value) "\nDisableUDP = true" else ""

            val httpConfig = if (_httpPort.value.isNotBlank()) {
                "\n\n[http]\nBindAddress = 127.0.0.1:${_httpPort.value}"
            } else ""

            val finalConfig = """
                $fileContent
                
                [Socks5]
                BindAddress = 127.0.0.1:${_socksPort.value}$authConfig$udpConfig$httpConfig
            """.trimIndent()

            val intent = Intent(context, AwgService::class.java).apply {
                action = AwgService.ACTION_START
                putExtra(AwgService.EXTRA_CONFIG, finalConfig)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun pingTunnel(host: String) {
        updatePingHost(host)
        viewModelScope.launch {
            _pingResult.value = "…"
            val result = withContext(Dispatchers.IO) { Appctr.pingTunnel(host) }
            _pingResult.value = result
        }
    }

    fun clearPingResult() { _pingResult.value = null }
    fun clearLogs() { Appctr.clearLogs() }
}