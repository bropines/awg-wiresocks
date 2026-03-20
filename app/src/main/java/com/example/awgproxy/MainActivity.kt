package com.example.awgproxy

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.*
import appctr.Appctr
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val isConfigLoaded = mutableStateOf(false)

    private val importConfigFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    saveAwgConfig(this, reader.readText())
                    isConfigLoaded.value = true
                    Toast.makeText(this, "Profile imported!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val exportLogsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(Appctr.getLogs())
                }
                Toast.makeText(this, "Logs saved!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Проверяем, есть ли уже сохраненный профиль
        isConfigLoaded.value = File(filesDir, "awg.conf").exists()

        setContent {
            MaterialTheme {
                MainAppScreen(
                    isConfigLoaded = isConfigLoaded,
                    onImportClick = { importConfigFileLauncher.launch("*/*") },
                    onExportLogsClick = { 
                        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                        exportLogsLauncher.launch("awg-logs-${sdf.format(Date())}.txt")
                    }
                )
            }
        }
    }
}

// Утилита для сохранения и фикса конфига (вырезаем старый MTU, ставим свой)
fun saveAwgConfig(context: Context, rawContent: String) {
    var fixed = rawContent.replace(Regex("(?m)^\\s*MTU\\s*=.*$"), "")
    fixed = fixed.replaceFirst("[Interface]", "[Interface]\nMTU = 1280")
    File(context.filesDir, "awg.conf").writeText(fixed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    isConfigLoaded: MutableState<Boolean>,
    onImportClick: () -> Unit,
    onExportLogsClick: () -> Unit
) {
    val navController = rememberNavController()
    var currentRoute by remember { mutableStateOf("home") }

    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentRoute = destination.route ?: "home"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AWG Proxy") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Status") },
                    label = { Text("Status") },
                    selected = currentRoute == "home",
                    onClick = { navController.navigate("home") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "Config") },
                    label = { Text("Config") },
                    selected = currentRoute == "config",
                    onClick = { navController.navigate("config") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, "Logs") },
                    label = { Text("Logs") },
                    selected = currentRoute == "logs",
                    onClick = { navController.navigate("logs") { launchSingleTop = true } }
                )
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(isConfigLoaded.value) }
            composable("config") { ConfigScreen(isConfigLoaded, onImportClick) }
            composable("logs") { LogsScreen(onExportLogsClick) }
        }
    }
}

@Composable
fun HomeScreen(isConfigLoaded: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("awg_prefs", Context.MODE_PRIVATE) }
    var isRunning by remember { mutableStateOf(Appctr.isRunning()) }

    LaunchedEffect(Unit) {
        while (true) {
            isRunning = Appctr.isRunning()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        val bgColor = if (isRunning) Color(0xFFDCF8C6) else MaterialTheme.colorScheme.surfaceVariant
        val contentColor = if (isRunning) Color(0xFF205023) else MaterialTheme.colorScheme.onSurfaceVariant

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = bgColor,
            modifier = Modifier.fillMaxWidth().height(160.dp).clickable {
                if (isRunning) {
                    context.startService(Intent(context, AwgService::class.java).apply { action = AwgService.ACTION_STOP })
                } else {
                    if (!isConfigLoaded) {
                        Toast.makeText(context, "Please load a profile in Config tab first!", Toast.LENGTH_LONG).show()
                        return@clickable
                    }

                    val wgConfPath = File(context.filesDir, "awg.conf").absolutePath
                    val socksPort = prefs.getString("socksPort", "1080") ?: "1080"
                    val httpPort = prefs.getString("httpPort", "8080") ?: "8080"

                    // Генерируем красивый микро-конфиг для движка
                    val finalConfig = """
                        WGConfig = $wgConfPath
                        
                        [Socks5]
                        BindAddress = 127.0.0.1:$socksPort
                        
                        [http]
                        BindAddress = 127.0.0.1:$httpPort
                    """.trimIndent()

                    val intent = Intent(context, AwgService::class.java).apply {
                        action = AwgService.ACTION_START
                        putExtra(AwgService.EXTRA_CONFIG, finalConfig)
                    }
                    ContextCompat.startForegroundService(context, intent)
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
    }
}

@Composable
fun ConfigScreen(isConfigLoaded: MutableState<Boolean>, onImportClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("awg_prefs", Context.MODE_PRIVATE) }
    
    var socksPort by remember { mutableStateOf(prefs.getString("socksPort", "1080") ?: "1080") }
    var httpPort by remember { mutableStateOf(prefs.getString("httpPort", "8080") ?: "8080") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        
        // Карточка статуса профиля
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AWG Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Status: ${if (isConfigLoaded.value) "Loaded ✅" else "Not Loaded ❌"}")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImportClick) { Text("Import File") }
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let {
                            saveAwgConfig(context, it)
                            isConfigLoaded.value = true
                            Toast.makeText(context, "Saved from clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Paste") }
                }
            }
        }

        // Настройки портов
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Proxy Ports", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = socksPort,
                    onValueChange = { socksPort = it.filter { c -> c.isDigit() }; prefs.edit().putString("socksPort", socksPort).apply() },
                    label = { Text("SOCKS5 Port") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = httpPort,
                    onValueChange = { httpPort = it.filter { c -> c.isDigit() }; prefs.edit().putString("httpPort", httpPort).apply() },
                    label = { Text("HTTP Port") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun LogsScreen(onExportClick: () -> Unit) {
    var logs by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        while (true) { logs = Appctr.getLogs(); delay(1000) }
    }
    LaunchedEffect(logs.length) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Engine Logs", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row {
                IconButton(onClick = onExportClick) { Icon(Icons.Default.Save, "Export") }
                IconButton(onClick = { Appctr.clearLogs(); logs = "" }) { Icon(Icons.Default.Delete, "Clear") }
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(16.dp).background(Color.Black, RoundedCornerShape(8.dp))) {
            SelectionContainer {
                Text(
                    text = logs.ifEmpty { "Waiting..." },
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFC8E6C9),
                    modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState)
                )
            }
        }
    }
}