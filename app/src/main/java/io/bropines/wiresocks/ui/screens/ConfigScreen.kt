package io.bropines.wiresocks.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.bropines.wiresocks.viewmodel.ProxyViewModel

fun extractValue(config: String, key: String): String {
    val regex = Regex("(?m)^\\s*$key\\s*=\\s*(.*)$", RegexOption.IGNORE_CASE)
    return regex.find(config)?.groupValues?.get(1)?.trim() ?: ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: ProxyViewModel) {
    val context = LocalContext.current
    
    val availableConfigs by viewModel.availableConfigs.collectAsState()
    val selectedConfig by viewModel.selectedConfig.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val rawConfig by viewModel.rawConfig.collectAsState()
    val socksPort by viewModel.socksPort.collectAsState()
    val httpPort by viewModel.httpPort.collectAsState()
    val socksUser by viewModel.socksUser.collectAsState()
    val socksPass by viewModel.socksPass.collectAsState()

    // Находим все нестандартные секции (например, [UDPProxyTunnel]), чтобы не стереть их при сохранении
    val extraSections by remember(rawConfig) {
        mutableStateOf(
            rawConfig.split(Regex("(?m)^\\s*\\["))
                .filter { it.isNotBlank() && !it.startsWith("Interface]", ignoreCase = true) && !it.startsWith("Peer]", ignoreCase = true) }
                .joinToString("\n\n") { "[$it".trimEnd() }
        )
    }

    // Interface
    var privateKey by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "PrivateKey")) }
    var address by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "Address")) }
    var dns by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "DNS")) }
    var mtu by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "MTU").ifEmpty { "1280" }) }

    // Peer
    var publicKey by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "PublicKey")) }
    var presharedKey by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "PresharedKey")) }
    var endpoint by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "Endpoint")) }
    var allowedIps by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "AllowedIPs").ifEmpty { "0.0.0.0/0, ::/0" }) }
    var keepalive by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "PersistentKeepalive").ifEmpty { "" }) }

    // Amnezia AWG
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
    var i2 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "I2")) }
    var i3 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "I3")) }
    var i4 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "I4")) }
    var i5 by remember(rawConfig) { mutableStateOf(extractValue(rawConfig, "I5")) }

    var showAdvanced by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.importConfig(context, uri)
        Toast.makeText(context, "Profile imported!", Toast.LENGTH_SHORT).show()
    }

    fun buildAndSaveConfig() {
        val sb = StringBuilder()

        sb.appendLine("[Interface]")
        sb.appendLine("PrivateKey = $privateKey")
        if (address.isNotBlank()) sb.appendLine("Address = $address")
        if (dns.isNotBlank()) sb.appendLine("DNS = $dns")
        if (mtu.isNotBlank()) sb.appendLine("MTU = $mtu")
        
        if (jc.isNotBlank()) sb.appendLine("Jc = $jc")
        if (jmin.isNotBlank()) sb.appendLine("Jmin = $jmin")
        if (jmax.isNotBlank()) sb.appendLine("Jmax = $jmax")
        if (s1.isNotBlank()) sb.appendLine("S1 = $s1")
        if (s2.isNotBlank()) sb.appendLine("S2 = $s2")
        if (h1.isNotBlank()) sb.appendLine("H1 = $h1")
        if (h2.isNotBlank()) sb.appendLine("H2 = $h2")
        if (h3.isNotBlank()) sb.appendLine("H3 = $h3")
        if (h4.isNotBlank()) sb.appendLine("H4 = $h4")
        if (i1.isNotBlank()) sb.appendLine("I1 = $i1")
        if (i2.isNotBlank()) sb.appendLine("I2 = $i2")
        if (i3.isNotBlank()) sb.appendLine("I3 = $i3")
        if (i4.isNotBlank()) sb.appendLine("I4 = $i4")
        if (i5.isNotBlank()) sb.appendLine("I5 = $i5")

        sb.appendLine()
        sb.appendLine("[Peer]")
        sb.appendLine("PublicKey = $publicKey")
        if (presharedKey.isNotBlank()) sb.appendLine("PresharedKey = $presharedKey")
        if (endpoint.isNotBlank()) sb.appendLine("Endpoint = $endpoint")
        if (allowedIps.isNotBlank()) sb.appendLine("AllowedIPs = $allowedIps")
        if (keepalive.isNotBlank()) sb.appendLine("PersistentKeepalive = $keepalive")

        // Сохраняем дополнительные туннели (UDP/TCP), если они были в файле
        if (extraSections.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(extraSections)
        }

        viewModel.saveCurrentConfig(sb.toString())
        Toast.makeText(context, "Configuration saved!", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Profile Management", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedConfig?.name ?: "No profiles found",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        availableConfigs.forEach { file ->
                            DropdownMenuItem(
                                text = { Text(file.name) },
                                onClick = { viewModel.selectConfig(file); expanded = false }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) { Text("Import") }
                    
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let {
                            val newName = "pasted_${System.currentTimeMillis()}"
                            viewModel.importConfigFromString(newName, it)
                            Toast.makeText(context, "Pasted & Saved as new", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Paste") }
                    
                    if (selectedConfig != null) {
                        OutlinedButton(
                            onClick = { 
                                viewModel.deleteSelectedConfig()
                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show() 
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Delete") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Local Proxy Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = socksPort, 
                        onValueChange = { viewModel.updateSocksPort(it) }, 
                        label = { Text("SOCKS5 Port") }, 
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = httpPort, 
                        onValueChange = { viewModel.updateHttpPort(it) }, 
                        label = { Text("HTTP Port") }, 
                        placeholder = { Text("Empty to disable") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = socksUser, 
                        onValueChange = { viewModel.updateSocksUser(it) }, 
                        label = { Text("Username") }, 
                        placeholder = { Text("Optional") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = socksPass, 
                        onValueChange = { viewModel.updateSocksPass(it) }, 
                        label = { Text("Password") }, 
                        placeholder = { Text("Optional") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Text(
                    text = "If username/password are set, Stealth Mode is enabled to drop unauthorized scanners.",
                    fontSize = 12.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (selectedConfig != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WireGuard Parameters", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(onClick = { buildAndSaveConfig() }) { Text("Save") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("[Interface]", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = privateKey, onValueChange = { privateKey = it }, label = { Text("PrivateKey") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = dns, onValueChange = { dns = it }, label = { Text("DNS (e.g. 1.1.1.1)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mtu,
                        onValueChange = { mtu = it },
                        label = { Text("MTU") },
                        supportingText = { Text("1280 — безопасный, 1420 — стандарт") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("[Peer]", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint (IP:Port)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = publicKey, onValueChange = { publicKey = it }, label = { Text("PublicKey") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = presharedKey, onValueChange = { presharedKey = it }, label = { Text("PresharedKey (если есть)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = allowedIps, onValueChange = { allowedIps = it }, label = { Text("AllowedIPs") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keepalive,
                        onValueChange = { keepalive = it },
                        label = { Text("PersistentKeepalive (сек)") },
                        supportingText = { Text("Пусто или 0 — экономит батарею, 25 — если соединение зависает") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            OutlinedTextField(value = i1, onValueChange = { i1 = it }, label = { Text("I1") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = i2, onValueChange = { i2 = it }, label = { Text("I2") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = i3, onValueChange = { i3 = it }, label = { Text("I3") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = i4, onValueChange = { i4 = it }, label = { Text("I4") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = i5, onValueChange = { i5 = it }, label = { Text("I5") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}