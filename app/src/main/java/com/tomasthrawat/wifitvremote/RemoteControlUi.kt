package com.tomasthrawat.wifitvremote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DESIGN_WIDTH = 300f
private const val DESIGN_HEIGHT = 619f

@Composable
private fun remoteBackground(): Color =
    if (isSystemInDarkTheme()) Color(0xFF000000) else Color(0xFFE8E7EA)

@Composable
private fun remoteSurface(): Color =
    if (isSystemInDarkTheme()) Color(0xFF181818) else Color(0xFFF0F0F4)

@Composable
private fun remoteSurface2(): Color =
    if (isSystemInDarkTheme()) Color(0xFF141414) else Color(0xFFF8F8FA)

@Composable
private fun remoteText(): Color =
    if (isSystemInDarkTheme()) Color(0xFFF4F4F4) else Color(0xFF151515)

@Composable
private fun remoteMuted(): Color =
    if (isSystemInDarkTheme()) Color(0xFF9B9B9B) else Color(0xFF77777B)

@Composable
private fun remoteBorder(): Color =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.08f)

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

private enum class Destination { REMOTE, APPS, CAST, SETTINGS }

@Composable
internal fun RemoteScreen(
    remote: RemoteUiActions,
    status: String,
    onDisconnect: () -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(Destination.REMOTE.name) }

    DisposableEffect(remote) {
        remote.setTextStateListener { }
        onDispose { remote.setTextStateListener(null) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(remoteBackground()),
        contentAlignment = Alignment.TopCenter
    ) {
        val scale = maxWidth.value / DESIGN_WIDTH
        Box(
            modifier = Modifier
                .width(DESIGN_WIDTH.dp)
                .height(DESIGN_HEIGHT.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
        ) {
            when (destination) {
                Destination.REMOTE.name -> ReferenceRemoteLayout(
                    remote = remote,
                    status = status,
                    onSetup = { destination = Destination.SETTINGS.name }
                )
                Destination.APPS.name -> ReferencePage { AppsPage(remote) }
                Destination.CAST.name -> ReferencePage { CastPage() }
                Destination.SETTINGS.name -> ReferencePage { SettingsPage(status, onDisconnect) }
            }
            ReferenceBottomNav(
                destination = destination,
                onDestination = { destination = it.name }
            )
        }
    }
}

@Composable
private fun ReferenceRemoteLayout(
    remote: RemoteUiActions,
    status: String,
    onSetup: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(remoteBackground())
    ) {
        ControlSurface(
            x = 23, y = 54, w = 46, h = 46,
            radius = 23, color = remoteSurface2(),
            onClick = remote::power
        ) {
            Text("⏻", color = remoteText(), fontSize = 18.sp)
        }

        Column(
            modifier = Modifier
                .offset(75.dp, 53.dp)
                .padding(top = 19.dp)
                .size(150.dp, 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Samsung Smart TV",
                color = remoteText(),
                fontSize = 15.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Box(
                Modifier
                    .padding(top = 1.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (status == "متصل" || status == "Connected") Color(0xFF4ED17B)
                        else remoteMuted()
                    )
            )
        }

        ControlSurface(
            x = 231, y = 54, w = 46, h = 46,
            radius = 23, color = remoteSurface2(),
            onClick = remote::input
        ) {
            Text("⌁", color = remoteText(), fontSize = 18.sp)
        }

        ReferenceTextButton("SETUP", 22, 132, 52, 24, onClick = onSetup)
        ReferenceTextButton("SOURCE", 224, 132, 54, 24, onClick = remote::input)

        DPadReference(remote, Modifier.offset(72.dp, 133.dp))

        ReferenceTextButton("EXIT", 23, 243, 46, 46, true, remote::back)
        ReferenceTextButton("CH-LIST", 230, 243, 47, 46, true, remote::menu)

        Row(
            modifier = Modifier
                .offset(93.dp, 303.dp)
                .size(114.dp, 33.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniGlyphButton("‹", remote::back)
            MiniGlyphButton("⌂", remote::home)
            MiniGlyphButton("123", { })
        }

        VerticalRocker(
            modifier = Modifier.offset(45.dp, 351.dp),
            label = "VOL",
            top = "+",
            middle = "●",
            bottom = "−",
            onTop = remote::volumeUp,
            onMiddle = remote::mute,
            onBottom = remote::volumeDown
        )

        VerticalRocker(
            modifier = Modifier.offset(209.dp, 351.dp),
            label = "CH",
            top = "+",
            middle = "•",
            bottom = "−",
            onTop = remote::channelUp,
            onMiddle = remote::menu,
            onBottom = remote::channelDown
        )

        MiniControl(100, 351, "≡", remote::menu)
        MiniControl(154, 351, "i", remote::input)
        WideControl(100, 406, "MUTE", remote::mute)

        StreamReferenceButton(
            x = 30,
            y = 489,
            text = "▶ YouTube",
            textColor = Color(0xFFFF3333),
            onClick = { remote.launchAppLink("https://www.youtube.com/") }
        )
        StreamReferenceButton(
            x = 113,
            y = 489,
            text = "NETFLIX",
            textColor = Color(0xFFE50914),
            onClick = { remote.launchAppLink("https://www.netflix.com/") }
        )
        StreamReferenceButton(
            x = 196,
            y = 489,
            text = "prime video",
            textColor = remoteText(),
            onClick = { remote.launchAppLink("https://www.primevideo.com/") }
        )
    }
}

@Composable
private fun ReferenceTextButton(
    text: String,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    fill: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(w.dp, h.dp)
            .clip(RoundedCornerShape(if (fill) 9.dp else 4.dp))
            .background(if (fill) remoteSurface2() else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = remoteText(),
            fontSize = if (text == "CH-LIST") 8.sp else 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ControlSurface(
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    radius: Int,
    color: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(w.dp, h.dp),
        color = color,
        shape = RoundedCornerShape(radius.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun DPadReference(remote: RemoteUiActions, modifier: Modifier) {
    Box(
        modifier
            .size(156.dp)
            .clip(CircleShape)
            .background(remoteSurface())
            .border(1.dp, remoteBorder(), CircleShape)
    ) {
        DPadKeyReference(Modifier.offset(55.dp, 7.dp), "▲", remote::up)
        DPadKeyReference(Modifier.offset(7.dp, 55.dp), "◀", remote::left)
        DPadKeyReference(Modifier.offset(109.dp, 55.dp), "▶", remote::right)
        DPadKeyReference(Modifier.offset(55.dp, 109.dp), "▼", remote::down)
        Surface(
            modifier = Modifier
                .offset(49.dp, 49.dp)
                .size(58.dp),
            color = remoteSurface2(),
            shape = CircleShape,
            onClick = remote::ok
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("OK", color = remoteText(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DPadKeyReference(modifier: Modifier, text: String, onClick: () -> Unit) {
    Surface(
        modifier = modifier.size(46.dp),
        color = Color.Transparent,
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = remoteText(), fontSize = 15.sp)
        }
    }
}

@Composable
private fun MiniGlyphButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(33.dp),
        color = remoteSurface2(),
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                color = remoteText(),
                fontSize = if (text == "123") 8.sp else 15.sp,
                fontWeight = if (text == "123") FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun VerticalRocker(
    modifier: Modifier,
    label: String,
    top: String,
    middle: String,
    bottom: String,
    onTop: () -> Unit,
    onMiddle: () -> Unit,
    onBottom: () -> Unit
) {
    Column(
        modifier
            .size(46.dp, 101.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(remoteSurface2())
            .border(1.dp, remoteBorder(), RoundedCornerShape(23.dp)),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        RockerGlyph(top, onTop)
        RockerGlyph(middle, onMiddle)
        RockerGlyph(bottom, onBottom)
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            color = remoteMuted(),
            textAlign = TextAlign.Center,
            fontSize = 7.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RockerGlyph(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(23.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = remoteText(), fontSize = 13.sp)
    }
}

@Composable
private fun MiniControl(x: Int, y: Int, label: String, onClick: () -> Unit) {
    ControlSurface(
        x, y, 46, 46, 10, remoteSurface2(), onClick
    ) {
        Text(label, color = remoteText(), fontSize = 13.sp)
    }
}

@Composable
private fun WideControl(x: Int, y: Int, label: String, onClick: () -> Unit) {
    ControlSurface(
        x, y, 101, 46, 10, remoteSurface2(), onClick
    ) {
        Text(label, color = remoteMuted(), fontSize = 8.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StreamReferenceButton(
    x: Int,
    y: Int,
    text: String,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(74.dp, 40.dp),
        color = remoteSurface2(),
        shape = RoundedCornerShape(9.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = textColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReferenceBottomNav(
    destination: String,
    onDestination: (Destination) -> Unit
) {
    val items = listOf(
        Triple(Destination.REMOTE, "⌁", "Remote"),
        Triple(Destination.APPS, "▦", "Apps"),
        Triple(Destination.CAST, "◒", "Cast"),
        Triple(Destination.SETTINGS, "⚙", "Settings")
    )
    Row(
        modifier = Modifier
             .offset(16.dp, 543.dp)
            .size(269.dp, 76.dp)
            .background(remoteBackground()),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        items.forEach { (item, icon, label) ->
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onDestination(item) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(23.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        icon,
                        color = if (destination == item.name) remoteText() else remoteMuted(),
                        fontSize = 13.sp
                    )
                }
                if (destination == item.name) {
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .width(18.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(remoteText())
                    )
                } else {
                    Box(Modifier.height(2.dp))
                }
                Text(
                    label,
                    color = if (destination == item.name) remoteText() else remoteMuted(),
                    fontSize = 7.sp,
                    fontWeight = if (destination == item.name) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ReferencePage(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(remoteBackground())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 78.dp)
    ) { content() }
}

@Composable
private fun AppsPage(remote: RemoteUiActions) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Apps", color = remoteText(), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Launch supported TV app links.", color = remoteMuted(), fontSize = 10.sp)
        listOf(
            "YouTube" to "https://www.youtube.com/",
            "Prime Video" to "https://www.primevideo.com/",
            "Netflix" to "https://www.netflix.com/"
        ).forEach { (name, link) ->
            Button(
                onClick = { remote.launchAppLink(link) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = remoteSurface(),
                    contentColor = remoteText()
                )
            ) {
                Text(name, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CastPage() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Cast", color = remoteText(), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Cast control surface.", color = remoteMuted(), fontSize = 10.sp)
    }
}

@Composable
private fun SettingsPage(status: String, onDisconnect: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Settings", color = remoteText(), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = remoteSurface()),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("Samsung Smart TV", color = remoteText(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(
                    status,
                    color = if (status == "متصل") Color(0xFF4ED17B) else remoteMuted(),
                    fontSize = 10.sp
                )
            }
        }
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            border = BorderStroke(1.dp, remoteBorder()),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Disconnect TV", fontSize = 10.sp)
        }
    }
}
