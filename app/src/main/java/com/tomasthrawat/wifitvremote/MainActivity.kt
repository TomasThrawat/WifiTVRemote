package com.tomasthrawat.wifitvremote

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext)
        AppLogger.i("ACTIVITY", "onCreate")
        setContent { WifiTvRemoteTheme { Screen() } }
    }

    override fun onDestroy() {
        AppLogger.i("ACTIVITY", "onDestroy")
        super.onDestroy()
    }
}

@Composable
private fun WifiTvRemoteTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen() {
    val context = LocalContext.current
    val discovery = remember { NsdDiscovery(context) }
    val devices = remember { mutableStateListOf<TvDevice>() }
    val scope = rememberCoroutineScope()
    var pairing by remember { mutableStateOf<TvPairing?>(null) }
    var remote by remember { mutableStateOf<TvRemote?>(null) }
    var connected by remember { mutableStateOf(false) }
    var showRemote by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Wi‑Fi فقط • جاهز للبحث") }

    fun scan() {
        AppLogger.i("UI_SCAN", "scan requested")
        devices.clear()
        status = "جاري البحث..."
        scope.launch {
            discovery.scan().collect { device ->
                AppLogger.i("DISCOVERY", "device found name=" + device.name + " host=" + device.host + " port=" + device.port)
                if (devices.none { it.host == device.host }) devices.add(device)
                status = "تم العثور على " + devices.size + " جهاز"
            }
        }
    }

    fun connect(device: TvDevice) {
        AppLogger.i("UI_CONNECT", "connect requested name=" + device.name + " host=" + device.host + " port=" + device.port)
        status = "جاري الاتصال بـ " + device.name + "..."
        lateinit var r: TvRemote
        val p = TvPairing(
            context = context,
            host = device.host,
            onCode = {
                AppLogger.i("PAIRING_UI", "TV requested pairing code")
                status = "أدخل رمز الاقتران الظاهر على التلفزيون."
            },
            onPaired = { identity ->
                AppLogger.i("PAIRING_UI", "pairing completed; starting remote connection")
                status = "تم الاقتران. جاري الاتصال..."
                r = TvRemote(
                    host = device.host,
                    id = identity,
                    onReady = {
                        AppLogger.i("REMOTE_UI", "remote ready")
                        connected = true
                        remote = r
                        pairing = null
                        status = "متصل"
                        showRemote = true
                    },
                    onError = { error ->
                        AppLogger.e("REMOTE_UI", "remote error", error)
                        connected = false
                        status = "خطأ: " + (error.message ?: error.javaClass.simpleName)
                    }
                )
                remote = r
                r.start()
            },
            onError = { error ->
                AppLogger.e("PAIRING_UI", "pairing error", error)
                status = "خطأ: " + (error.message ?: error.javaClass.simpleName)
            }
        )
        pairing = p
        p.start()
    }

    if (showRemote && connected && remote != null) {
        RemoteScreen(
            remote = remote!!,
            status = status,
            onBack = { showRemote = false }
        )
    } else {
        ConnectionScreen(
            devices = devices,
            pairing = pairing,
            code = code,
            status = status,
            onCodeChange = { code = it.take(6) },
            onScan = { scan() },
            onConnect = { connect(it) },
            onSubmitCode = {
                val submittedCode = code
                AppLogger.i("PAIRING_UI", "pairing code submitted length=" + submittedCode.trim().length)
                scope.launch {
                    val p = pairing ?: return@launch
                    val success = p.submitCode(submittedCode)
                    status = if (success) {
                        "تم إرسال الرمز. انتظار التلفزيون..."
                    } else {
                        "تعذر إرسال الرمز. تأكد من إدخال الرمز السداسي الصحيح."
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionScreen(
    devices: List<TvDevice>,
    pairing: TvPairing?,
    code: String,
    status: String,
    onCodeChange: (String) -> Unit,
    onScan: () -> Unit,
    onConnect: (TvDevice) -> Unit,
    onSubmitCode: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("الاتصال", style = MaterialTheme.typography.titleLarge)
                        Text("Wi‑Fi TV Remote", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("الاتصال بالتلفزيون", style = MaterialTheme.typography.headlineSmall)
                        Text("ابحث عن Android TV واتصل به عبر Wi‑Fi.")
                        Text(status, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                Button(onClick = onScan, Modifier.fillMaxWidth()) {
                    Text("البحث عن أجهزة التلفزيون")
                }
            }

            items(devices, key = { it.host }) { device ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(device.host, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(
                            onClick = { onConnect(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("اتصال")
                        }
                    }
                }
            }

            pairing?.let {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("إقران التلفزيون", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = code,
                                onValueChange = onCodeChange,
                                label = { Text("رمز الاقتران") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = onSubmitCode,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("تأكيد الرمز")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteScreen(
    remote: TvRemote,
    status: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                title = {
                    Column {
                        Text("جهاز التحكم", style = MaterialTheme.typography.titleLarge)
                        Text(status, style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ remote.power() }, Modifier.weight(1f)) { Text("Power") }
                OutlinedButton({ remote.home() }, Modifier.weight(1f)) { Text("Home") }
                OutlinedButton({ remote.back() }, Modifier.weight(1f)) { Text("Back") }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                        FilledTonalButton({ remote.up() }) { Text("↑") }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ remote.right() }, Modifier.weight(1f)) { Text("→") }
                        FilledTonalButton({ remote.ok() }, Modifier.weight(1f)) { Text("OK") }
                        FilledTonalButton({ remote.left() }, Modifier.weight(1f)) { Text("←") }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                        FilledTonalButton({ remote.down() }) { Text("↓") }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ remote.volumeDown() }, Modifier.weight(1f)) { Text("Vol −") }
                OutlinedButton({ remote.mute() }, Modifier.weight(1f)) { Text("Mute") }
                OutlinedButton({ remote.volumeUp() }, Modifier.weight(1f)) { Text("Vol +") }
            }

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ remote.playPause() }, Modifier.weight(1f)) { Text("Play / Pause") }
                FilledTonalButton({ remote.stop() }, Modifier.weight(1f)) { Text("Stop") }
            }
        }
    }
}

