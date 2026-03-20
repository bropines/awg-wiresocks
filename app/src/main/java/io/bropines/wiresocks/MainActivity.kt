package io.bropines.wiresocks

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import io.bropines.wiresocks.ui.screens.MainAppScreen
import io.bropines.wiresocks.ui.theme.AwgProxyTheme
import io.bropines.wiresocks.viewmodel.ProxyViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    
    // Подключаем наш "мозг"
    private val viewModel: ProxyViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Запрос разрешений на уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Запрашиваем иммунитет от энергосбережения (чтобы не убивало в фоне)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        // 3. Проверяем, открыли ли приложение кликом по файлу .conf
        handleFileIntent(intent)

        // 4. Запускаем UI с поддержкой темной темы
        setContent {
            AwgProxyTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    // Обрабатываем файл, если приложение уже висело в фоне
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleFileIntent(intent)
    }

    private fun handleFileIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val content = BufferedReader(InputStreamReader(inputStream)).readText()
                        viewModel.saveConfig(content)
                        Toast.makeText(this, "Profile loaded from file!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to read config file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}