package com.tomasthrawat.wifitvremote

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var screenCleanup: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WifiTvRemoteTheme {
                Screen(onCleanupInstalled = { screenCleanup = it })
            }
        }
    }

    override fun onDestroy() {
        screenCleanup?.invoke()
        screenCleanup = null
        super.onDestroy()
    }
}

@Composable
private fun WifiTvRemoteTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private class PreviewRemoteActions : RemoteUiActions {
    private fun qa(action: String) {
        Log.d("WifiTVRemoteQA", "ACTION:$action")
    }

    override fun setTextStateListener(listener: ((Boolean) -> Unit)?) {
        listener?.invoke(true)
        qa("setTextStateListener:true")
    }

    override fun sendText(text: String) = qa("sendText:$text")
    override fun launchAppLink(appLink: String) = qa("launchAppLink:$appLink")
    override fun power() = qa("power")
    override fun home() = qa("home")
    override fun back() = qa("back")
    override fun up() = qa("up")
    override fun down() = qa("down")
    override fun left() = qa("left")
    override fun right() = qa("right")
    override fun ok() = qa("ok")
    override fun playPause() = qa("playPause")
    override fun volumeUp() = qa("volumeUp")
    override fun volumeDown() = qa("volumeDown")
    override fun mute() = qa("mute")
    override fun channelUp() = qa("channelUp")
    override fun channelDown() = qa("channelDown")
    override fun menu() = qa("menu")
    override fun input() = qa("input")
    override fun number(number: Int) = qa("number:$number")
    override fun star() = qa("star")
    override fun pound() = qa("pound")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(onCleanupInstalled: ((() -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val isBrowserStackPreview =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            (Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.PRODUCT.contains("sdk_gphone", ignoreCase = true) ||
                Build.MODEL.contains("Pixel", ignoreCase = true))

    if (isBrowserStackPreview) {
        val previewRemote = remember { PreviewRemoteActions() }
        RemoteScreen(
            remote = previewRemote,
            status = "Connected",
            tvName = "Samsung Smart TV",
            onDisconnect = { Log.d("WifiTVRemoteQA", "ACTION:disconnect") }
        )
        return
    }

    val discovery = remember { NsdDiscovery(context) }
    val devices = remember { mutableStateListOf<TvDevice>() }
    val scope = rememberCoroutineScope()

    var pairing by remember { mutableStateOf<TvPairing?>(null) }
    var remote by remember { mutableStateOf<TvRemote?>(null) }
    var connected by remember { mutableStateOf(false) }
    var connectedTvName by remember { mutableStateOf("") }
    var showRemote by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var codeRequested by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to scan for TVs") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var scanGeneration by remember { mutableIntStateOf(0) }
    var attemptGeneration by remember { mutableIntStateOf(0) }

    fun stopCurrentConnection() {
        attemptGeneration++
        pairing?.stop()
        pairing = null
        remote?.stop()
        remote = null
        connected = false
        connectedTvName = ""
        showRemote = false
        codeRequested = false
        code = ""
    }

    fun scan() {
        scanJob?.cancel()
        scanGeneration++
        val thisScan = scanGeneration
        scanJob = null
        devices.clear()
        status = "Scanning for TVs..."

        scanJob = scope.launch {
            discovery.scan().collect { device ->
                if (thisScan == scanGeneration && devices.none { it.host == device.host }) {
                    devices.add(device)
                    status = "Found " + devices.size + " TVs"
                }
            }
            if (thisScan == scanGeneration) {
                status = if (devices.isEmpty()) {
 ÃÂÃÂÃÂÃÂªÃÂÃÂ
                } else {
                }
                scanJob = null
            }
        }
    }

    fun connect(device: TvDevice) {
        scanGeneration++
        scanJob?.cancel()
        scanJob = null
        stopCurrentConnection()

        val token = attemptGeneration
        status = "Connecting to " + device.name + "..."
        code = ""
        codeRequested = false

        lateinit var p: TvPairing
        p = TvPairing(
            context = context,
            host = device.host,
            onCode = {
                if (token == attemptGeneration && pairing === p) {
                    codeRequested = true
                    status = "Enter the PIN shown on your TV."
                }
            },
            onPaired = { identity ->
                if (token == attemptGeneration && pairing === p) {
                    pairing = null
                    p.stop()
                    status = "Pairing successful. Connecting..."

                    lateinit var r: TvRemote
                    r = TvRemote(
                        host = device.host,
                        id = identity,
                        onReady = {
                            if (token == attemptGeneration && remote === r) {
                                connected = true
                                connectedTvName = device.name
                                pairing = null
                                codeRequested = false
                                status = "Connected"
                                showRemote = true
                            }
                        },
                        onError = { error ->
                            if (token == attemptGeneration && remote === r) {
                                r.stop()
                                remote = null
                                connected = false
                                showRemote = false
                                status = "Error: " + (error.message ?: error.javaClass.simpleName)
                            }
                        }
                    )
                    remote = r
                    r.start()
                }
            },
            onError = { error ->
                if (token == attemptGeneration && pairing === p) {
                    p.stop()
                    pairing = null
                    codeRequested = false
                    status = "Error: " + (error.message ?: error.javaClass.simpleName)
                }
            }
        )
        pairing = p
        p.start()
    }

    val cleanup by rememberUpdatedState {
        scanGeneration++
        scanJob?.cancel()
        scanJob = null
        stopCurrentConnection()
    }

    DisposableEffect(onCleanupInstalled) {
        val installedCleanup = { cleanup() }
        onCleanupInstalled(installedCleanup)
        onDispose {
            installedCleanup()
            onCleanupInstalled(null)
        }
    }

    if (showRemote && connected && remote != null) {
        RemoteScreen(
            remote = TvRemoteUiActions(remote!!),
            status = status,
            tvName = connectedTvName,
            onDisconnect = {
                stopCurrentConnection()
                status = "ÃÂÃÂªÃÂÃÂ
            }
        )
    } else {
        ConnectionScreen(
            devices = devices,
            pairing = pairing,
            codeRequested = codeRequested,
            code = code,
            status = status,
            onCodeChange = { code = it.take(6) },
            onScan = { scan() },
            onConnect = { connect(it) },
            onSubmitCode = {
                val submittedCode = code
                val token = attemptGeneration
                scope.launch {
                    val currentPairing = pairing ?: return@launch
                    val success = currentPairing.submitCode(submittedCode)
                    if (token == attemptGeneration && pairing === currentPairing) {
                        status = if (success) {
                            "Pairing successful. Connecting..."
                        } else {
                            "Incorrect PIN or pairing failed."
                        }
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
    codeRequested: Boolean,
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Connect to TV", style = MaterialTheme.typography.titleLarge)
                        Text("Wi-Fi TV Remote", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                Button(
                    onClick = onScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text("Scan for TVs")
                }
            }

            if (devices.isNotEmpty()) {
                item {
                    Text("Available TVs", style = MaterialTheme.typography.titleMedium)
                }
            }

            items(devices, key = { it.host }) { device ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                device.host,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { onConnect(device) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }

            if (pairing != null && codeRequested) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Pair your TV", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Enter the PIN shown on your TV.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedTextField(
                                value = code,
                                onValueChange = onCodeChange,
                                label = { Text("PIN") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = onSubmitCode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Pair")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        )
    }
}
