package com.example.awgproxy.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.awgproxy.ui.theme.color_active_dark
import com.example.awgproxy.ui.theme.color_active_light
import com.example.awgproxy.viewmodel.ProxyViewModel

@Composable
fun HomeScreen(viewModel: ProxyViewModel) {
    val context = LocalContext.current
    val isRunning by viewModel.isRunning.collectAsState()
    val isConfigLoaded by viewModel.isConfigLoaded.collectAsState()
    val socksPort by viewModel.socksPort.collectAsState()
    val httpPort by viewModel.httpPort.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Адаптивный цвет для активной кнопки
        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
        val activeColor = if (isDarkTheme) color_active_dark else color_active_light
        
        val bgColor = if (isRunning) activeColor else MaterialTheme.colorScheme.surfaceVariant
        val contentColor = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = bgColor,
            modifier = Modifier.fillMaxWidth().height(160.dp).clickable {
                if (!isRunning && !isConfigLoaded) {
                    Toast.makeText(context, "Load a profile in Config tab first!", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.toggleProxy(context)
                }
            },
            tonalElevation = 4.dp
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = contentColor, modifier = Modifier.size(48.dp).padding(bottom = 16.dp))
                Text(if (isRunning) "Active" else "Stopped", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = contentColor)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        if (isRunning) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Active Proxies", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("SOCKS5: 127.0.0.1:$socksPort", color = MaterialTheme.colorScheme.primary)
                    Text("HTTP: 127.0.0.1:$httpPort", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}