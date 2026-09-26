package com.tomasthrawat.wifitvremote

import android.os.Build
import android.os.Bundle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(onCleanupInstalled: ((() -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val discovery = remember { NsdDiscovery(context) }
    val devices = remember { mutableStateListOf<TvDevice>() }
    val scope = rememberCoroutineScope()

    var pairing by remember { mutableStateOf<TvPairing?>(null) }
    var remote by remember { mutableStateOf<TvRemote?>(null) }
    var connected by remember { mutableStateOf(false) }
    var showRemote by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var codeRequested by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Wi‑Fi فقط • جاهز للبحث") }
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
        status = "جاري البحث..."

        scanJob = scope.launch {
            discovery.scan().collect { device ->
                if (thisScan == scanGeneration && devices.none { it.host == device.host }) {
                    devices.add(device)
                    status = "تم العثور على ${devices.size} جهاز"
                }
            }
            if (thisScan == scanGeneration) {
                status = if (devices.isEmpty()) {
                    "لم يتم العثور على أجهزة"
                } else {
                    "تم العثور على ${devices.size} جهاز"
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
        status = "جاري الاتصال بـ ${device.name}..."
        code = ""
        codeRequested = false

        lateinit var p: TvPairing
        p = TvPairing(
            context = context,
            host = device.host,
            onCode = {
                if (token == attemptGeneration && pairing === p) {
                    codeRequested = true
                    status = "أدخل رمز الاقتران الظاهر على التلفزيون."
                }
            },
            onPaired = { identity ->
                if (token == attemptGeneration && pairing === p) {
                    pairing = null
                    p.stop()
                    status = "تم الاقتران. جاري الاتصال..."

                    lateinit var r: TvRemote
                    r = TvRemote(
                        host = device.host,
                        id = identity,
                        onReady = {
                            if (token == attemptGeneration && remote === r) {
                                connected = true
                                pairing = null
                                codeRequested = false
                                status = "متصل"
                                showRemote = true
                            }
                        },
                        onError = { error ->
                            if (token == attemptGeneration && remote === r) {
                                r.stop()
                                remote = null
                                connected = false
                                showRemote = false
                                status = "خطأ: " + (error.message ?: error.javaClass.simpleName)
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
                    status = "خطأ: " + (error.message ?: error.javaClass.simpleName)
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
            remote = remote!!,
            status = status,
            onDisconnect = {
                stopCurrentConnection()
                status = "تم فصل الاتصال"
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
                            "تم إرسال الرمز. انتظار التلفزيون..."
                        } else {
                            "تعذر إرسال الرمز. تأكد من إدخال الرمز السداسي الصحيح."
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
                        Text("الاتصال", style = MaterialTheme.typography.titleLarge)
                        Text("Wi‑Fi TV Remote", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
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
                    Text("البحث عن أجهزة التلفزيون")
                }
            }

            if (devices.isNotEmpty()) {
                item {
                    Text("الأجهزة القريبة", style = MaterialTheme.typography.titleMedium)
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
                            Text("اتصال")
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
                            Text("إقران التلفزيون", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "أدخل الرمز الظاهر على شاشة التلفزيون.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedTextField(
                                value = code,
                                onValueChange = onCodeChange,
                                label = { Text("رمز الاقتران") },
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
    onDisconnect: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var textReady by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("جهاز التحكم", style = MaterialTheme.typography.titleLarge)
                        Text(status, style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    TextButton(onClick = onDisconnect) {
                        Text("فصل")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
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
                ControlSection(title = "التنقل") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilledTonalButton(
                                onClick = { remote.up() },
                                modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 52.dp)
                            ) { Text("↑") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { remote.left() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                            ) { Text("←") }
                            Button(
                                onClick = { remote.ok() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                            ) { Text("OK") }
                            FilledTonalButton(
                                onClick = { remote.right() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                            ) { Text("→") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilledTonalButton(
                                onClick = { remote.down() },
                                modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 52.dp)
                            ) { Text("↓") }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { remote.back() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                    ) { Text("Back") }
                    OutlinedButton(
                        onClick = { remote.home() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                    ) { Text("Home") }
                    OutlinedButton(
                        onClick = { remote.power() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                    ) { Text("Power") }
                }
            }

            item {
                ControlSection(title = "الصوت") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { remote.volumeDown() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                        ) { Text("Vol −") }
                        OutlinedButton(
                            onClick = { remote.mute() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                        ) { Text("Mute") }
                        OutlinedButton(
                            onClick = { remote.volumeUp() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                        ) { Text("Vol +") }
                    }
                }
            }

            item {
                ControlSection(title = "الوسائط") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { remote.playPause() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                        ) {
                            Text("تشغيل / إيقاف مؤقت")
                        }
                    }
                }
            }

            item {
                ControlSection(title = "الكتابة على التلفزيون") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (textReady) {
                                "جاهز لإرسال النص إلى حقل الكتابة النشط على التلفزيون."
                            } else {
                                "افتح حقل كتابة على التلفزيون أولًا، ثم اكتب هنا."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                            placeholder = { Text("اكتب هنا…") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { remote.sendText(text) },
                                enabled = textReady && text.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) { Text("إرسال") }
                            OutlinedButton(
                                onClick = {
                                    text = ""
                                    remote.clearText()
                                },
                                enabled = textReady,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) { Text("مسح") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { remote.delete() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) { Text("حذف") }
                            OutlinedButton(
                                onClick = { remote.space() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) { Text("مسافة") }
                            OutlinedButton(
                                onClick = { remote.enter() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) { Text("إدخال") }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(remote) {
        val callback = { ready: Boolean -> textReady = ready }
        remote.setTextStateListener(callback)
        onDispose { remote.setTextStateListener(null) }
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