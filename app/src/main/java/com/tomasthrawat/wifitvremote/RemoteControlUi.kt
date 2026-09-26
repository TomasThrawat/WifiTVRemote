package com.tomasthrawat.wifitvremote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

private val AppBlack = Color(0xFF000000)
private val AppSurface = Color(0xFF171717)
private val AppSurface2 = Color(0xFF222222)
private val AppBlue = Color(0xFF1D6EFF)
private val AppText = Color(0xFFF5F5F5)
private val AppMuted = Color(0xFF8D8D93)
private val AppGreen = Color(0xFF25C77A)

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
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(AppBlack)
                    .border(1.dp, Color.White.copy(alpha = 0.08f)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomItem("Remote", destination == Destination.REMOTE.name) {
                    destination = Destination.REMOTE.name
                }
                BottomItem("Apps", destination == Destination.APPS.name) {
                    destination = Destination.APPS.name
                }
                BottomItem("Settings", destination == Destination.SETTINGS.name) {
                    destination = Destination.SETTINGS.name
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(AppBlack)
                .then(Modifier)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AppBlack)
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
                        onDisconnect = onDisconnect,
                        onSetup = { destination = Destination.SETTINGS.name }
                    )
                    Destination.APPS.name -> AppsPage(remote)
                    Destination.SETTINGS.name -> SettingsPage(status, onDisconnect)
                }
            }
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .height(46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) AppBlue else Color.Transparent)
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
    onDisconnect: () -> Unit,
    onSetup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBlack)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(43.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DecorativeHeaderButton("⏻")
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    "Samsung Smart TV",
                    color = AppText,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(AppGreen)
                    )
                    Text(
                        if (status == "متصل") "Connected" else status,
                        color = AppGreen,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            DecorativeHeaderButton("◖")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactButton("SETUP", Modifier.weight(1f), onSetup)
            CompactButton("SOURCE", Modifier.weight(1f), remote::input)
            CompactButton("EXIT", Modifier.weight(1f), remote::back)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VolumeRocker(remote, Modifier.size(width = 50.dp, height = 148.dp))
            DPad(remote, Modifier.size(150.dp))
            ChannelCluster(remote, Modifier.width(54.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(31.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallCircleButton("↶", remote::back)
            SmallCircleButton("⌂", remote::home)
            SmallCircleButton("⋯", remote::menu)
        }

        NumberPad(
            remote = remote,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(182.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            color = AppSurface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 7.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    enabled = textReady,
                    singleLine = true,
                    placeholder = {
                        Text("Type on TV", color = AppMuted)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedBorderColor = AppBlue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
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
            modifier = Modifier
                .fillMaxWidth()
                .height(33.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            StreamButton("NETFLIX", Modifier.weight(1f)) {
                remote.launchAppLink("https://www.netflix.com/")
            }
            StreamButton("YouTube", Modifier.weight(1f)) {
                remote.launchAppLink("https://www.youtube.com/")
            }
            StreamButton("Prime Video", Modifier.weight(1f)) {
                remote.launchAppLink("https://www.primevideo.com/")
            }
        }
    }
}

@Composable
private fun DecorativeHeaderButton(label: String) {
    Surface(
        modifier = Modifier.size(30.dp),
        color = AppSurface2,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = AppText,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun VolumeRocker(
    remote: TvRemote,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(25.dp))
            .background(AppSurface)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(25.dp)),
        verticalArrangement = Arrangement.Center
    ) {
        RockerButton("+", remote::volumeUp, Modifier.weight(1f))
        RockerButton("●", remote::mute, Modifier.weight(1f))
        RockerButton("−", remote::volumeDown, Modifier.weight(1f))
    }
}

@Composable
private fun RockerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = AppText,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChannelCluster(
    remote: TvRemote,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        RemoteSideButton("LIST", remote::menu)
        RemoteSideButton("CH+", remote::channelUp)
        RemoteSideButton("CH−", remote::channelDown)
    }
}

@Composable
private fun SmallCircleButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(31.dp),
        color = AppSurface2,
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = AppText,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DPad(
    remote: TvRemote,
    modifier: Modifier
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(AppSurface)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        DPadKey(Modifier.align(Alignment.TopCenter), "▲", remote::up)
        DPadKey(Modifier.align(Alignment.CenterStart), "◀", remote::left)
        DPadKey(Modifier.align(Alignment.CenterEnd), "▶", remote::right)
        DPadKey(Modifier.align(Alignment.BottomCenter), "▼", remote::down)
        Surface(
            modifier = Modifier.size(58.dp),
            color = AppBlue,
            shape = CircleShape,
            onClick = remote::ok
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DPadKey(
    modifier: Modifier,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.size(40.dp),
        color = Color.White.copy(alpha = 0.045f),
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = AppText)
        }
    }
}

@Composable
private fun NumberPad(
    remote: TvRemote,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        ).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { digit ->
                    NumberKey(
                        digit,
                        Modifier.weight(1f),
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
private fun NumberKey(
    digit: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = AppSurface,
        shape = RoundedCornerShape(9.dp),
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
        modifier = modifier.height(33.dp),
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppSurface2,
            contentColor = AppText
        ),
        contentPadding = PaddingValues(horizontal = 5.dp)
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
            .background(AppBlack)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
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
            .background(AppBlack)
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
            shape = RoundedCornerShape(14.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppText),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Text("Disconnect TV")
        }
    }
}