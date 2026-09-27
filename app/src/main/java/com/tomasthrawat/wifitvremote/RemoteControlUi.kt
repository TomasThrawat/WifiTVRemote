package com.tomasthrawat.wifitvremote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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

@Composable
private fun remoteBackground(): Color =
    if (isSystemInDarkTheme()) Color(0xFF0D0F12) else Color(0xFFE7EAF2)

@Composable
private fun remoteSurface(): Color =
    if (isSystemInDarkTheme()) Color(0xFF17191D) else Color(0xFFF8F9FC)

@Composable
private fun remoteSurface2(): Color =
    if (isSystemInDarkTheme()) Color(0xFF23262B) else Color(0xFFFFFFFF)

@Composable
private fun remoteText(): Color =
    if (isSystemInDarkTheme()) Color(0xFFF5F5F7) else Color(0xFF17191D)

@Composable
private fun remoteMuted(): Color =
    if (isSystemInDarkTheme()) Color(0xFF91959E) else Color(0xFF6D717A)

@Composable
private fun remoteGreen(): Color =
    if (isSystemInDarkTheme()) Color(0xFF25C77A) else Color(0xFF168B58)

@Composable
private fun remoteBorder(): Color =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)

@Composable
private fun remoteKeyTint(): Color =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.045f) else Color.Black.copy(alpha = 0.035f)

private val AppBlue = Color(0xFF1D6EFF)

internal interface RemoteUiActions {
    fun setTextStateListener(listener: ((Boolean) -> Unit)?)
    fun sendText(text: String)
    fun launchAppLink(appLink: String)
    fun power()
    fun home()
    fun back()
    fun up()
    fun down()
    fun left()
    fun right()
    fun ok()
    fun volumeUp()
    fun volumeDown()
    fun mute()
    fun channelUp()
    fun channelDown()
    fun menu()
    fun input()
    fun number(number: Int)
    fun star()
    fun pound()
}

internal class TvRemoteUiActions(private val remote: TvRemote) : RemoteUiActions {
    override fun setTextStateListener(listener: ((Boolean) -> Unit)?) = remote.setTextStateListener(listener)
    override fun sendText(text: String) = remote.sendText(text)
    override fun launchAppLink(appLink: String) = remote.launchAppLink(appLink)
    override fun power() = remote.power()
    override fun home() = remote.home()
    override fun back() = remote.back()
    override fun up() = remote.up()
    override fun down() = remote.down()
    override fun left() = remote.left()
    override fun right() = remote.right()
    override fun ok() = remote.ok()
    override fun volumeUp() = remote.volumeUp()
    override fun volumeDown() = remote.volumeDown()
    override fun mute() = remote.mute()
    override fun channelUp() = remote.channelUp()
    override fun channelDown() = remote.channelDown()
    override fun menu() = remote.menu()
    override fun input() = remote.input()
    override fun number(number: Int) = remote.number(number)
    override fun star() = remote.star()
    override fun pound() = remote.pound()
}

private enum class Destination { REMOTE, APPS, SETTINGS }

@Composable
internal fun RemoteScreen(
    remote: RemoteUiActions,
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
        containerColor = remoteBackground(),
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(remoteBackground())
                    .border(1.dp, remoteBorder()),
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
                .background(remoteBackground())
                .then(Modifier)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(remoteBackground())
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
            color = if (selected) remoteText() else remoteMuted(),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CompactButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, remoteBorder()),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = remoteText()),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun RemoteReferenceLayout(
    remote: RemoteUiActions,
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
            .background(remoteBackground())
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
                    color = remoteText(),
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
                            .background(remoteGreen())
                    )
                    Text(
                        if (status == "متصل") "Connected" else status,
                        color = remoteGreen(),
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
            color = remoteSurface(),
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
                        Text("Type on TV", color = remoteMuted())
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = remoteText(),
                        unfocusedTextColor = remoteText(),
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
                    Text("Send", color = if (textReady) AppBlue else remoteMuted())
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
            StreamButton("prime video", Modifier.weight(1f)) {
                remote.launchAppLink("https://www.primevideo.com/")
            }
        }
    }
}

@Composable
private fun DecorativeHeaderButton(label: String) {
    Surface(
        modifier = Modifier.size(30.dp),
        color = remoteSurface2(),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = remoteText(),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun VolumeRocker(
    remote: RemoteUiActions,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(25.dp))
            .background(remoteSurface())
            .border(1.dp, remoteBorder(), RoundedCornerShape(25.dp)),
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
                color = remoteText(),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChannelCluster(
    remote: RemoteUiActions,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        RemoteSideButton("CH-LIST", remote::menu)
        RemoteSideButton("CH+", remote::channelUp)
        RemoteSideButton("CH−", remote::channelDown)
    }
}

@Composable
private fun RemoteSideButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        color = remoteSurface2(),
        shape = RoundedCornerShape(9.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = remoteText(),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SmallCircleButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(31.dp),
        color = remoteSurface2(),
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = remoteText(),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DPad(
    remote: RemoteUiActions,
    modifier: Modifier
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(remoteSurface())
            .border(1.dp, remoteBorder(), CircleShape),
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
        color = remoteKeyTint(),
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = remoteText())
        }
    }
}

@Composable
private fun NumberPad(
    remote: RemoteUiActions,
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
        color = remoteSurface(),
        shape = RoundedCornerShape(9.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                digit,
                color = remoteText(),
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
            containerColor = remoteSurface2(),
            contentColor = remoteText()
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
private fun AppsPage(remote: RemoteUiActions) {
    Column(
        Modifier
            .fillMaxSize()
            .background(remoteBackground())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Apps",
            color = remoteText(),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Launch supported TV app links.",
            color = remoteMuted(),
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
                    containerColor = remoteSurface(),
                    contentColor = remoteText()
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
            .background(remoteBackground())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Settings",
            color = remoteText(),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = remoteSurface()),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Samsung Smart TV", color = remoteText(), fontWeight = FontWeight.SemiBold)
                Text(status, color = remoteGreen())
                Text(
                    "Remote controls use the authenticated TV connection.",
                    color = remoteMuted(),
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
            colors = ButtonDefaults.outlinedButtonColors(contentColor = remoteText()),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Text("Disconnect TV")
        }
    }
}