package com.example.awgproxy.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.awgproxy.viewmodel.ProxyViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

// Утилита для вытаскивания значений из конфига по ключу
fun extractValue(config: String, key: String): String {
    val regex = Regex("(?m)^\\s*$key\\s*=\\s*(.*)$", RegexOption.IGNORE_CASE)
    return regex.find(config)?.groupValues?.get(1)?.trim() ?: ""
}

@Composable
fun ConfigScreen(viewModel: ProxyViewModel) {
    val context = LocalContext.current
    val isConfigLoaded by viewModel.isConfigLoaded.collectAsState()
    val rawConfig by viewModel.rawConfig.collectAsState()
    val socksPort by viewModel.socksPort.collectAsState()
    val httpPort by viewModel.httpPort.collectAsState()

    // Локальные состояния для полей редактирования
    var privateKey by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "PrivateKey")) }
    var address by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "Address")) }
    var dns by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "DNS")) }
    var publicKey by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "PublicKey")) }
    var endpoint by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "Endpoint")) }
    var allowedIps by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "AllowedIPs").ifEmpty { "0.0.0.0/0, ::/0" }) }
    
    // Amnezia параметры
    var jc by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "Jc")) }
    var jmin by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "Jmin")) }
    var jmax by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "Jmax")) }
    var s1 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "S1")) }
    var s2 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "S2")) }
    var h1 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "H1")) }
    var h2 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "H2")) }
    var h3 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "H3")) }
    var h4 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "H4")) }
    var i1 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "I1")) }

    var showAdvanced by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = BufferedReader(InputStreamReader(inputStream)).readText()
                viewModel.saveConfig(content)
                Toast.makeText(context, "Profile imported!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Функция сборки конфига обратно
    fun buildAndSaveConfig() {
        val newConfig = """
            [Interface]
            PrivateKey = $privateKey
            Address = $address
            DNS = $dns
            MTU = 1280
            ${if (jc.isNotBlank()) "Jc = $jc" else ""}
            ${if (jmin.isNotBlank()) "Jmin = $jmin" else ""}
            ${if (jmax.isNotBlank()) "Jmax = $jmax" else ""}
            ${if (s1.isNotBlank()) "S1 = $s1" else ""}
            ${if (s2.isNotBlank()) "S2 = $s2" else ""}
            ${if (h1.isNotBlank()) "H1 = $h1" else ""}
            ${if (h2.isNotBlank()) "H2 = $h2" else ""}
            ${if (h3.isNotBlank()) "H3 = $h3" else ""}
            ${if (h4.isNotBlank()) "H4 = $h4" else ""}
            ${if (i1.isNotBlank()) "I1 = $i1" else ""}

            [Peer]
            PublicKey = $publicKey
            Endpoint = $endpoint
            AllowedIPs = $allowedIps
        """.trimIndent()
        viewModel.saveConfig(newConfig)
        Toast.makeText(context, "Configuration saved!", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        
        // --- 1. КАРТОЧКА УПРАВЛЕНИЯ ПРОФИЛЕМ ---
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Profile Management", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Status: ${if (isConfigLoaded) "Loaded ✅" else "Not Loaded ❌"}")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) { Text("Import") }
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let {
                            viewModel.saveConfig(it)
                            Toast.makeText(context, "Pasted & Saved", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Paste") }
                }
            }
        }

        // --- 2. НАСТРОЙКИ ПРОКСИ ---
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Local Proxy Ports", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = socksPort, onValueChange = { viewModel.updateSocksPort(it) }, label = { Text("SOCKS5") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = httpPort, onValueChange = { viewModel.updateHttpPort(it) }, label = { Text("HTTP") }, modifier = Modifier.weight(1f))
                }
            }
        }

        // --- 3. РЕДАКТОР WIREGUARD ---
        if (isConfigLoaded) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("WireGuard Parameters", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(onClick = { buildAndSaveConfig() }) { Text("Save") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint (IP:Port)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = privateKey, onValueChange = { privateKey = it }, label = { Text("PrivateKey") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = publicKey, onValueChange = { publicKey = it }, label = { Text("PublicKey") }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Amnezia Advanced / Obfuscation", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        Icon(if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = jc, onValueChange = { jc = it }, label = { Text("Jc") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = jmin, onValueChange = { jmin = it }, label = { Text("Jmin") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = jmax, onValueChange = { jmax = it }, label = { Text("Jmax") }, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = s1, onValueChange = { s1 = it }, label = { Text("S1") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = s2, onValueChange = { s2 = it }, label = { Text("S2") }, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = h1, onValueChange = { h1 = it }, label = { Text("H1") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = h2, onValueChange = { h2 = it }, label = { Text("H2") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = h3, onValueChange = { h3 = it }, label = { Text("H3") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = h4, onValueChange = { h4 = it }, label = { Text("H4") }, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = i1, onValueChange = { i1 = it }, label = { Text("I1 (Payload)") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}