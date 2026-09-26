package com.tomasthrawat.wifitvremote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val RemoteBackground = Color(0xFF03050A)
private val RemoteSurface = Color(0xFF0A0F1B)
private val RemoteSurfaceHigh = Color(0xFF121A2B)
private val RemoteBlue = Color(0xFF2E73FF)
private val RemoteBlueBright = Color(0xFF4A8BFF)
private val RemoteText = Color(0xFFF5F7FF)
private val RemoteMuted = Color(0xFF8F9AB4)
private val RemoteGreen = Color(0xFF31D18A)

private enum class RemoteDestination { REMOTE, APPS, SETTINGS }

@Composable
fun RemoteScreen(
    remote: TvRemote,
    status: String,
    onDisconnect: () -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(RemoteDestination.REMOTE.name) }
    var text by remember { mutableStateOf("") }
    var textReady by remember { mutableStateOf(false) }

    DisposableEffect(remote) {
        val callback = { ready: Boolean -> textReady = ready }
        remote.setTextStateListener(callback)
        onDispose { remote.setTextStateListener(null) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = RemoteBackground,
        bottomBar = {
            NavigationBar(
                containerColor = RemoteSurface,
                contentColor = RemoteText,
                tonalElevation = 0.dp
            ) {
                RemoteNavigationItem(destination == RemoteDestination.REMOTE.name, "⌂", "Remote") {
                    destination = RemoteDestination.REMOTE.name
                }
                RemoteNavigationItem(destination == RemoteDestination.APPS.name, "▦", "Apps") {
                    destination = RemoteDestination.APPS.name
                }
                RemoteNavigationItem(destination == RemoteDestination.SETTINGS.name, "⚙", "Settings") {
                    destination = RemoteDestination.SETTINGS.name
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF02040A), RemoteBackground, Color(0xFF061A42))
                    )
                )
        ) {
            when (destination) {
                RemoteDestination.REMOTE.name -> RemoteHomeContent(
                    remote, status, text, textReady,
                    { text = it },
                    { remote.sendText(text); text = "" },
                    onDisconnect
                )
                RemoteDestination.APPS.name -> AppsContent(remote)
                RemoteDestination.SETTINGS.name -> SettingsContent(status, onDisconnect)
            }
        }
    }
}

@Composable
private fun RemoteNavigationItem(
    selected: Boolean,
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Text(
                icon,
                color = if (selected) RemoteBlueBright else RemoteMuted,
                style = MaterialTheme.typography.titleMedium
            )
        },
        label = {
            Text(label, color = if (selected) RemoteText else RemoteMuted)
        },
        alwaysShowLabel = true
    )
}

@Composable
private fun RemoteHomeContent(
    remote: TvRemote,
    status: String,
    text: String,
    textReady: Boolean,
    onTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Universal Remote",
                    color = RemoteText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(RemoteGreen)
                    )
                    Text("Connected", color = RemoteGreen, style = MaterialTheme.typography.labelMedium)
                }
            }
            Surface(
                Modifier.size(40.dp).clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                color = RemoteSurfaceHigh,
                shape = CircleShape,
                onClick = onDisconnect
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = RemoteText, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Text(status, color = RemoteMuted, style = MaterialTheme.typography.labelSmall)

        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(300.dp).clip(CircleShape).background(
                    Brush.radialGradient(
                        listOf(
                            RemoteBlue.copy(alpha = 0.28f),
                            RemoteBlue.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SideControlColumn(
                    RemoteUiSpec.volumeControls,
                    remote::volumeUp,
                    remote::mute,
                    remote::volumeDown
                )
                DPad(remote)
                SideControlColumn(
                    RemoteUiSpec.channelControls,
                    remote::channelUp,
                    remote::menu,
                    remote::channelDown
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteUiSpec.quickActions.forEach { label ->
                QuickAction(
                    label,
                    Modifier.weight(1f)
                ) {
                    when (label) {
                        "Back" -> remote.back()
                        "Home" -> remote.home()
                        "Power" -> remote.power()
                    }
                }
            }
        }

        Surface(
            Modifier.fillMaxWidth(),
            color = RemoteSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Type on TV", color = RemoteMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    enabled = textReady,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RemoteText,
                        unfocusedTextColor = RemoteText,
                        focusedBorderColor = RemoteBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledBorderColor = Color.White.copy(alpha = 0.04f)
                    )
                )
                TextButton(
                    onClick = onSendText,
                    enabled = textReady && text.isNotEmpty()
                ) {
                    Text("Send", color = if (textReady) RemoteBlueBright else RemoteMuted)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun AppsContent(remote: TvRemote) {
    var appLink by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Apps",
            color = RemoteText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Launch an Android TV app through an app link.",
            color = RemoteMuted,
            style = MaterialTheme.typography.bodyMedium
        )

        listOf(
            "YouTube" to "https://www.youtube.com/",
            "Netflix" to "https://www.netflix.com/",
            "Prime Video" to "https://www.primevideo.com/",
            "Disney+" to "https://www.disneyplus.com/"
        ).forEach { (name, link) ->
            Button(
                onClick = { remote.launchAppLink(link) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RemoteSurfaceHigh,
                    contentColor = RemoteText
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(name)
            }
        }

        OutlinedTextField(
            value = appLink,
            onValueChange = { appLink = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Custom app link") },
            placeholder = { Text("https://...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RemoteText,
                unfocusedTextColor = RemoteText,
                focusedBorderColor = RemoteBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedLabelColor = RemoteBlueBright
            )
        )

        Button(
            onClick = { remote.launchAppLink(appLink); appLink = "" },
            enabled = appLink.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = RemoteBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Open app link")
        }
    }
}

@Composable
private fun SettingsContent(status: String, onDisconnect: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Settings",
            color = RemoteText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RemoteSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Connection", color = RemoteText, fontWeight = FontWeight.SemiBold)
                Text(status, color = RemoteGreen)
                Text(
                    "Remote protocol connection is active.",
                    color = RemoteMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RemoteText),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Text("Disconnect TV")
        }
    }
}

@Composable
private fun SideControlColumn(
    labels: List<String>,
    onFirst: () -> Unit,
    onMiddle: () -> Unit,
    onLast: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        labels.forEachIndexed { index, label ->
            SideButton(
                label,
                when (index) {
                    0 -> onFirst
                    1 -> onMiddle
                    else -> onLast
                }
            )
        }
    }
}

@Composable
private fun SideButton(label: String, onClick: () -> Unit) {
    Surface(
        Modifier.size(width = 46.dp, height = 58.dp),
        color = RemoteSurfaceHigh.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = RemoteText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DPad(remote: TvRemote) {
    Box(Modifier.size(212.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(212.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(RemoteSurfaceHigh, Color(0xFF090F20)))
            ).border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
        )
        DPadButton(Modifier.align(Alignment.TopCenter), "↑", remote::up)
        DPadButton(Modifier.align(Alignment.CenterStart), "←", remote::left)
        DPadButton(Modifier.align(Alignment.CenterEnd), "→", remote::right)
        DPadButton(Modifier.align(Alignment.BottomCenter), "↓", remote::down)
        Button(
            onClick = remote::ok,
            modifier = Modifier.size(82.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = RemoteBlue,
                contentColor = Color.White
            )
        ) {
            Text("OK", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DPadButton(modifier: Modifier, text: String, onClick: () -> Unit) {
    Surface(
        modifier.size(56.dp),
        color = Color.White.copy(alpha = 0.045f),
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = RemoteText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun QuickAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RemoteText),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
