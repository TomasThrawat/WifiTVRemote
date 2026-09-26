package com.tomasthrawat.wifitvremote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

private val AppBlack = Color(0xFF050505)
private val AppSurface = Color(0xFF1A1A1C)
private val AppSurface2 = Color(0xFF232326)
private val AppBlue = Color(0xFF1F6FFF)
private val AppText = Color(0xFFF7F7F8)
private val AppMuted = Color(0xFFA4A4AA)
private val AppGreen = Color(0xFF22C77A)

private enum class Destination { REMOTE, APPS, SETTINGS }

@Composable
fun RemoteScreen(
    remote: TvRemote,
    status: String,
    onDisconnect: () -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(Destination.REMOTE.name) }
    var text by remember { mutableStateOf("") }
    var textReady by remember { mutableStateOf(false) }

    DisposableEffect(remote) {
        val callback = { ready: Boolean -> textReady = ready }
        remote.setTextStateListener(callback)
        onDispose { remote.setTextStateListener(null) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppBlack,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppSurface)
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomItem("⌂", "Remote", destination == Destination.REMOTE.name) {
                    destination = Destination.REMOTE.name
                }
                BottomItem("▦", "Apps", destination == Destination.APPS.name) {
                    destination = Destination.APPS.name
                }
                BottomItem("⚙", "Settings", destination == Destination.SETTINGS.name) {
                    destination = Destination.SETTINGS.name
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(AppBlack, Color(0xFF090A0D), Color(0xFF111827))
                    )
                )
        ) {
            when (destination) {
                Destination.REMOTE.name -> RemoteReferenceLayout(
                    remote = remote,
                    status = status,
                    text = text,
                    textReady = textReady,
                    onTextChange = { text = it },
                    onSend = {
                        remote.sendText(text)
                        text = ""
                    },
                    onDisconnect = onDisconnect
                )
                Destination.APPS.name -> AppsPage(remote)
                Destination.SETTINGS.name -> SettingsPage(status, onDisconnect)
            }
        }
    }
}

@Composable
private fun BottomItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            icon,
            color = if (selected) AppBlue else AppMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            label,
            color = if (selected) AppText else AppMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun RemoteReferenceLayout(
    remote: TvRemote,
    status: String,
    text: String,
    textReady: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Samsung Smart TV",
                    color = AppText,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AppGreen)
                    )
                    Text(
                        if (status == "متصل") "Connected" else status,
                        color = AppGreen,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = .10f), CircleShape),
                color = AppSurface2,
                shape = CircleShape,
                onClick = onDisconnect
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = AppText, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopRemoteButton("SETUP", Modifier.weight(1f)) { }
            TopRemoteButton("SOURCE", Modifier.weight(1f)) { remote.input() }
        }

        ControlPanel(
            remote = remote,
            onExit = remote::back,
            onList = remote::menu,
            onChannel = remote::channelUp
        )

        NumberPad(remote)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppSurface.copy(alpha = .96f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    Modifier.weight(1f),
                    singleLine = true,
                    enabled = textReady,
                    placeholder = { Text("Type on TV", color = AppMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedBorderColor = AppBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = .10f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledBorderColor = Color.White.copy(alpha = .06f)
                    )
                )
                TextButton(
                    onClick = onSend,
                    enabled = textReady && text.isNotEmpty()
                ) {
                    Text("Send", color = if (textReady) AppBlue else AppMuted)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            StreamButton("Prime Video", Modifier.weight(1f)) {
                remote.launchAppLink("https://www.primevideo.com/")
            }
            StreamButton("YouTube", Modifier.weight(1f)) {
                remote.launchAppLink("https://www.youtube.com/")
            }
            StreamButton("NETFLIX", Modifier.weight(1f)) {
                remote.launchAppLink("https://www.netflix.com/")
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TopRemoteButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppText),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))
    ) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ControlPanel(
    remote: TvRemote,
    onExit: () -> Unit,
    onList: () -> Unit,
    onChannel: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SmallRemoteButton("EXIT", Modifier.weight(1f), onExit)
        DPad(remote, Modifier.weight(1.6f))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SmallRemoteButton("LIST", Modifier.fillMaxWidth(), onList)
            SmallRemoteButton("CH+", Modifier.fillMaxWidth(), onChannel)
            SmallRemoteButton("CH−", Modifier.fillMaxWidth(), remote::channelDown)
        }
    }
}

@Composable
private fun SmallRemoteButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(42.dp),
        color = AppSurface2,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = AppText,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DPad(remote: TvRemote, modifier: Modifier = Modifier) {
    Box(modifier.size(165.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(165.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(AppSurface2, Color(0xFF101115))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
        )
        DPadKey(Modifier.align(Alignment.TopCenter), "↑", remote::up)
        DPadKey(Modifier.align(Alignment.CenterStart), "←", remote::left)
        DPadKey(Modifier.align(Alignment.CenterEnd), "→", remote::right)
        DPadKey(Modifier.align(Alignment.BottomCenter), "↓", remote::down)
        Button(
            onClick = remote::ok,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppBlue,
                contentColor = Color.White
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text("OK", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DPadKey(modifier: Modifier, text: String, onClick: () -> Unit) {
    Surface(
        modifier = modifier.size(46.dp),
        color = Color.White.copy(alpha = .045f),
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = AppText)
        }
    }
}

@Composable
private fun NumberPad(remote: TvRemote) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        ).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { digit ->
                    NumberKey(
                        digit = digit,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (digit) {
                                "*" -> remote.star()
                                "#" -> remote.pound()
                                else -> remote.number(digit.toInt())
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberKey(digit: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(44.dp),
        color = AppSurface,
        shape = RoundedCornerShape(11.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                digit,
                color = AppText,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun StreamButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppSurface2,
            contentColor = AppText
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
    ) {
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun AppsPage(remote: TvRemote) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Apps",
            color = AppText,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Launch supported TV app links.",
            color = AppMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        listOf(
            "YouTube" to "https://www.youtube.com/",
            "Prime Video" to "https://www.primevideo.com/",
            "Netflix" to "https://www.netflix.com/"
        ).forEach { (name, link) ->
            Button(
                onClick = { remote.launchAppLink(link) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppSurface,
                    contentColor = AppText
                )
            ) {
                Text(name)
            }
        }
    }
}

@Composable
private fun SettingsPage(status: String, onDisconnect: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Settings",
            color = AppText,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Samsung Smart TV", color = AppText, fontWeight = FontWeight.SemiBold)
                Text(status, color = AppGreen)
                Text(
                    "Remote controls use the authenticated TV connection.",
                    color = AppMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        }
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppText),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))
        ) {
            Text("Disconnect TV")
        }
    }
}
