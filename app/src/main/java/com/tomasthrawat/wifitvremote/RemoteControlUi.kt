package com.tomasthrawat.wifitvremote

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import remote.RemoteKeyCode
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.dp

private val RemoteBackground = Color(0xFF05070F)
private val RemoteSurface = Color(0xFF0B1020)
private val RemoteSurfaceHigh = Color(0xFF121A2E)
private val RemoteBlue = Color(0xFF246BFF)
private val RemoteBlueBright = Color(0xFF3D82FF)
private val RemoteText = Color(0xFFF5F7FF)
private val RemoteMuted = Color(0xFF8F9AB4)
private val RemoteGreen = Color(0xFF31D18A)

@Composable
fun RemoteScreen(
    remote: TvRemote,
    status: String,
    onDisconnect: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var textReady by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF02040A), RemoteBackground, Color(0xFF06163A))
                )
            )
            .padding(horizontal = 18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Universal Remote",
                        color = RemoteText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(RemoteGreen)
                        )
                        Text(
                            status,
                            color = RemoteGreen,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                    color = RemoteSurfaceHigh,
                    shape = CircleShape,
                    onClick = onDisconnect
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("\u00D7", color = RemoteText, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(290.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    RemoteBlue.copy(alpha = 0.34f),
                                    RemoteBlue.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SideControlColumn(
                        labels = RemoteUiSpec.volumeControls,
                        onFirst = { remote.volumeUp() },
                        onMiddle = { remote.mute() },
                        onLast = { remote.volumeDown() }
                    )

                    DPad(remote)

                    SideControlColumn(
                        labels = RemoteUiSpec.channelControls,
                        onFirst = { remote.channelUp() },
                        onMiddle = { remote.menu() },
                        onLast = { remote.channelDown() }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteUiSpec.quickActions.forEach { label ->
                    QuickAction(
                        label = label,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (label) {
                                "Back" -> remote.back()
                                "Home" -> remote.home()
                                "Power" -> remote.power()
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = RemoteSurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Type on TV", color = RemoteMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        enabled = textReady,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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
                        onClick = {
                            remote.sendText(text)
                            text = ""
                        },
                        enabled = textReady && text.isNotEmpty()
                    ) {
                        Text("Send", color = if (textReady) RemoteBlueBright else RemoteMuted)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteUiSpec.bottomNavigation.forEachIndexed { index, label ->
                    Text(
                        label,
                        color = if (index == 0) RemoteText else RemoteMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
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
private fun SideControlColumn(
    labels: List<String>,
    onFirst: () -> Unit,
    onMiddle: () -> Unit,
    onLast: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        labels.forEachIndexed { index, label ->
            SideButton(
                label = label,
                onClick = when (index) {
                    0 -> onFirst
                    1 -> onMiddle
                    else -> onLast
                }
            )
        }
    }
}

@Composable
private fun SideButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(width = 46.dp, height = 58.dp),
        color = RemoteSurfaceHigh.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        shadowElevation = 0.dp
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
    Box(
        modifier = Modifier.size(212.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(212.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(RemoteSurfaceHigh, Color(0xFF090F20))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
        )

        DPadButton(
            modifier = Modifier.align(Alignment.TopCenter),
            text = "\u2191",
            onClick = { remote.up() }
        )
        DPadButton(
            modifier = Modifier.align(Alignment.CenterStart),
            text = "\u2190",
            onClick = { remote.left() }
        )
        DPadButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            text = "\u2192",
            onClick = { remote.right() }
        )
        DPadButton(
            modifier = Modifier.align(Alignment.BottomCenter),
            text = "\u2193",
            onClick = { remote.down() }
        )

        Button(
            onClick = { remote.ok() },
            modifier = Modifier.size(82.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = RemoteBlue,
                contentColor = Color.White
            )
        ) {
            Text(
                "OK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DPadButton(
    modifier: Modifier,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.size(56.dp),
        color = Color.White.copy(alpha = 0.045f),
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                color = RemoteText,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RemoteText),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.08f)
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
