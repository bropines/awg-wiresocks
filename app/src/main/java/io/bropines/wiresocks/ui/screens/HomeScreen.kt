package io.bropines.wiresocks.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.bropines.wiresocks.ui.theme.color_active_dark
import io.bropines.wiresocks.ui.theme.color_active_light
import io.bropines.wiresocks.viewmodel.ProxyViewModel

@Composable
fun HomeScreen(viewModel: ProxyViewModel) {
    val context = LocalContext.current
    val isRunning by viewModel.isRunning.collectAsState()
    val selectedConfig by viewModel.selectedConfig.collectAsState()
    val bindIp by viewModel.bindIp.collectAsState()
    val socksPort by viewModel.socksPort.collectAsState()
    val httpPort by viewModel.httpPort.collectAsState()
    val pingResult by viewModel.pingResult.collectAsState()
    val pingHost by viewModel.pingHost.collectAsState()

    var localPingHost by remember(pingHost) { mutableStateOf(pingHost) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(32.dp))

        val activeColor = if (isSystemInDarkTheme()) color_active_dark else color_active_light
        val bgColor = if (isRunning) activeColor else MaterialTheme.colorScheme.surfaceVariant
        val contentColor = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = bgColor,
            modifier = Modifier.fillMaxWidth().height(160.dp).clickable {
                if (!isRunning && selectedConfig == null) {
                    Toast.makeText(context, "Import a profile in Config tab first!", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.toggleProxy(context)
                    viewModel.clearPingResult()
                }
            },
            tonalElevation = 4.dp
        ) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, tint = contentColor, modifier = Modifier.size(48.dp).padding(bottom = 16.dp))
                Text(if (isRunning) "Active" else "Stopped", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = contentColor)
                if (selectedConfig != null) {
                    Text(selectedConfig!!.name, fontSize = 14.sp, color = contentColor.copy(alpha = 0.8f))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isRunning) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Active Proxies", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("SOCKS5: $bindIp:$socksPort", color = MaterialTheme.colorScheme.primary)
                    if (httpPort.isNotBlank()) {
                        Text("HTTP:   $bindIp:$httpPort", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NetworkCheck, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connection Test", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = localPingHost,
                        onValueChange = { localPingHost = it },
                        label = { Text("Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { viewModel.pingTunnel(localPingHost.trim()) }, enabled = pingResult != "…") {
                            Text(if (pingResult == "…") "Testing…" else "Test via tunnel")
                        }
                        pingResult?.let { result ->
                            Text(text = result, color = if (result.startsWith("ERR")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}