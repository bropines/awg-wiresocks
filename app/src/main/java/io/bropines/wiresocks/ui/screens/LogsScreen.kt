package io.bropines.wiresocks.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import appctr.Appctr
import io.bropines.wiresocks.viewmodel.ProxyViewModel
import kotlinx.coroutines.delay
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(viewModel: ProxyViewModel) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer -> writer.write(logs) }
            }
            Toast.makeText(context, "Logs exported!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            logs = Appctr.getLogs()
            delay(1000)
        }
    }
    LaunchedEffect(logs.length) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Engine Logs", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("AWG Logs", logs))
                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentCopy, "Copy") }
                
                IconButton(onClick = {
                    val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                    exportLauncher.launch("awg-logs-${sdf.format(Date())}.txt")
                }) { Icon(Icons.Default.Save, "Export") }
                
                IconButton(onClick = { viewModel.clearLogs(); logs = "" }) { Icon(Icons.Default.Delete, "Clear") }
            }
        }
        
        // Подложка, которая меняет цвет в зависимости от системной темы
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
        ) {
            SelectionContainer {
                Text(
                    text = logs.ifEmpty { "Waiting for logs..." },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState)
                )
            }
        }
    }
}