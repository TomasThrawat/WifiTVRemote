package com.tomasthrawat.wifitvremote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection

private const val DESIGN_WIDTH = 300f
private const val DESIGN_HEIGHT = 619f

@Composable
private fun remoteBackground(): Color =
    if (isSystemInDarkTheme()) Color.Black else Color(0xFFF7F6FA)

@Composable
private fun remoteSurface(): Color =
    if (isSystemInDarkTheme()) Color(0xFF222222) else Color.White

@Composable
private fun remoteSurface2(): Color =
    if (isSystemInDarkTheme()) Color(0xFF222222) else Color.White

@Composable
private fun remoteText(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFFFFFF) else Color(0xFF333333)

@Composable
private fun remoteMuted(): Color =
    if (isSystemInDarkTheme()) Color(0xFF9B9B9B) else Color(0xFF66666C)

@Composable
private fun remoteBorder(): Color =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.08f)

@Composable
private fun remotePressedSurface(): Color =
    if (isSystemInDarkTheme()) Color(0xFF2A2A2A) else Color(0xFFE3E3E7)

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
    fun playPause()
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
    override fun playPause() = remote.playPause()
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
    tvName: String,
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
        val scale = minOf(
            maxWidth.value / DESIGN_WIDTH,
            maxHeight.value / DESIGN_HEIGHT
        )
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
                Destination.REMOTE.name -> CompositionLocalProvider(
                    LocalLayoutDirection provides RemoteUiSpec.coordinateLayoutDirection
                ) {
                    ReferenceRemoteLayout(
                        remote = remote,
                        status = status,
                        tvName = tvName,
                        onSetup = { destination = Destination.SETTINGS.name }
                    )
                }
                Destination.APPS.name -> ReferencePage { AppsPage(remote) }
                Destination.CAST.name -> ReferencePage { CastPage() }
                Destination.SETTINGS.name -> ReferencePage { SettingsPage(tvName, status, onDisconnect) }
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
    tvName: String,
    onSetup: () -> Unit
) {
    var numberPadOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = numberPadOpen) {
        numberPadOpen = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(remoteBackground())
    ) {
        // Top status row: power, connection pill, speaker.
        ControlSurface(
            x = 20, y = 54, w = 46, h = 46,
            radius = 23, color = remoteSurface2(),
            onClick = remote::power,
            contentDescription = "Power"
        ) {
            ReferenceGlyph(Glyph.POWER, Color(0xFFFF2A6D), Modifier.size(22.dp))
        }

        ReferenceStatusPill(
            x = 72,
            y = 54,
            tvName = tvName,
            connected = status.equals("Connected", ignoreCase = true)
        )

        ControlSurface(
            x = 234, y = 54, w = 46, h = 46,
            radius = 23, color = remoteSurface2(),
            onClick = remote::mute,
            contentDescription = "Mute"
        ) {
            ReferenceGlyph(Glyph.SPEAKER, remoteText(), Modifier.size(20.dp))
        }

        // Setup and source sit on the second row, beside the navigation pad.
        ControlSurface(
            x = 20, y = 116, w = 46, h = 46,
            radius = 23, color = remoteSurface2(),
            onClick = onSetup,
            contentDescription = "Setup"
        ) {
            Text("SETUP", color = remoteText(), fontSize = 7.sp, fontWeight = FontWeight.Medium)
        }

        ControlSurface(
            x = 234, y = 116, w = 46, h = 46,
            radius = 23, color = remoteSurface2(),
            onClick = remote::input,
            contentDescription = "Source"
        ) {
            Text("SOURCE", color = remoteText(), fontSize = 7.sp, fontWeight = FontWeight.Medium)
        }

        DPadReference(
            remote = remote,
            modifier = Modifier.offset(72.dp, 136.dp)
        )

        // Side buttons align with the lower half of the D-pad.
        ReferenceTextButton(
            text = "EXIT",
            x = 20, y = 249, w = 46, h = 42,
            fill = true,
            onClick = remote::back
        )
        ReferenceTextButton(
            text = "CH-LIST",
            x = 234, y = 249, w = 46, h = 42,
            fill = true,
            onClick = remote::menu
        )

        // Small reference-mode strip.
        ReferenceModeStrip(
            x = 94,
            y = 315,
            onFirst = remote::input,
            onSecond = remote::mute,
            onNumbers = { numberPadOpen = true }
        )

        DualRocker(
            modifier = Modifier.offset(43.dp, 361.dp),
            label = "VOL",
            onUp = remote::volumeUp,
            onDown = remote::volumeDown,
            upIcon = "+",
            downIcon = "−"
        )

        DualRocker(
            modifier = Modifier.offset(211.dp, 361.dp),
            label = "CH",
            onUp = remote::channelUp,
            onDown = remote::channelDown,
            upIcon = "⌃",
            downIcon = "⌄"
        )

        // Center navigation: Home, Play/Pause, then a wide Back pill.
        CompactNavControl(
            x = 103, y = 360,
            contentDescription = "Home",
            onClick = remote::home
        ) {
            ReferenceGlyph(Glyph.HOME, remoteText(), Modifier.size(18.dp))
        }

        CompactNavControl(
            x = 159, y = 360,
            contentDescription = "Play/Pause",
            onClick = remote::playPause
        ) {
            ReferenceGlyph(Glyph.PLAY_PAUSE, remoteText(), Modifier.size(18.dp))
        }

        WideControl(
            x = 100, y = 414,
            label = "BACK",
            onClick = remote::back
        )

        ReferencePageIndicator(y = 478.dp)

        // Three streaming shortcuts, centered like the reference.
        StreamReferenceButton(
            x = 24, y = 503,
            text = "YouTube",
            textColor = Color(0xFFFF3333),
            onClick = { remote.launchAppLink("https://www.youtube.com/") }
        )
        StreamReferenceButton(
            x = 115, y = 503,
            text = "NETFLIX",
            textColor = Color(0xFFE50914),
            onClick = { remote.launchAppLink("https://www.netflix.com/") }
        )
        StreamReferenceButton(
            x = 206, y = 503,
            text = "prime video",
            textColor = remoteText(),
            onClick = { remote.launchAppLink("https://www.primevideo.com/") }
        )

        if (numberPadOpen) {
            NumberPadOverlay(
                remote = remote,
                onClose = { numberPadOpen = false }
            )
        }
    }
}

@Composable
private fun ReferenceStatusPill(
    x: Int,
    y: Int,
    tvName: String,
    connected: Boolean
) {
    Row(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(162.dp, 46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(remoteSurface2())
            .border(1.dp, remoteBorder(), RoundedCornerShape(23.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (connected) Color(0xFF4CD964) else remoteMuted())
        )
        Text(
            tvName.ifBlank { "TV" },
            modifier = Modifier.padding(start = 6.dp),
            color = remoteText(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ReferenceModeStrip(
    x: Int,
    y: Int,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    onNumbers: () -> Unit
) {
    Surface(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(112.dp, 31.dp),
        color = remoteSurface2(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, remoteBorder())
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple("◌", "Input mode", onFirst),
                Triple("⌕", "Mute", onSecond),
                Triple("123", "Number pad", onNumbers)
            ).forEach { (label, description, action) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(onClick = action)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = remoteText(),
                        fontSize = if (label == "123") 8.sp else 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferencePageIndicator(y: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .offset(140.dp, y)
            .width(20.dp)
            .height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                Modifier
                    .size(if (index == 0) 5.dp else 4.dp)
                    .clip(CircleShape)
                    .background(if (index == 0) remoteText() else remoteMuted())
            )
        }
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
            .clickable(onClick = onClick)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = remoteText(),
            fontSize = if (text == "CH-LIST") 7.sp else 8.sp,
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
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(w.dp, h.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
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
            .background(if (isSystemInDarkTheme()) Color(0xFF2A2A2A) else Color.White)
            .border(1.dp, remoteBorder(), CircleShape)
    ) {
        DPadKeyReference(Modifier.offset(55.dp, 7.dp), "â²", remote::up)
        DPadKeyReference(Modifier.offset(7.dp, 55.dp), "â", remote::left)
        DPadKeyReference(Modifier.offset(109.dp, 55.dp), "â¶", remote::right)
        DPadKeyReference(Modifier.offset(55.dp, 109.dp), "â¼", remote::down)
        Surface(
            modifier = Modifier
                .offset(49.dp, 49.dp)
                .size(58.dp),
            color = if (isSystemInDarkTheme()) Color(0xFF121212) else Color(0xFFF5F5F5),
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
        modifier = modifier
            .size(46.dp)
            .semantics { contentDescription = text },
        color = Color.Transparent,
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = remoteText(), fontSize = 15.sp)
        }
    }
}

private enum class Glyph { POWER, REMOTE, HOME, APPS, CAST, SETTINGS, SPEAKER, PLAY_PAUSE, BACK }

@Composable
private fun ReferenceGlyph(kind: Glyph, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = (size.minDimension * 0.075f).coerceAtLeast(1.3f)
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (kind) {
            Glyph.POWER -> {
                drawArc(
                    color = tint, startAngle = -45f, sweepAngle = 270f, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round)
                )
                drawLine(
                    tint,
                    androidx.compose.ui.geometry.Offset(cx, size.height * 0.08f),
                    androidx.compose.ui.geometry.Offset(cx, size.height * 0.46f),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
            }
            Glyph.HOME -> {
                val left = size.width * 0.18f
                val right = size.width * 0.82f
                val roofY = size.height * 0.40f
                val baseY = size.height * 0.78f
                val midX = size.width * 0.50f
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left, roofY)
                    lineTo(midX, size.height * 0.14f)
                    lineTo(right, roofY)
                    lineTo(right, baseY)
                    lineTo(left, baseY)
                    close()
                }
                drawPath(
                    p, tint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        stroke, cap = StrokeCap.Round, join = StrokeJoin.Round
                    )
                )
            }
            Glyph.REMOTE -> {
                val w = size.width * 0.46f
                val h = size.height * 0.78f
                val l = (size.width - w) / 2f
                val t = (size.height - h) / 2f
                drawRoundRect(
                    tint,
                    topLeft = androidx.compose.ui.geometry.Offset(l, t),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                )
                drawCircle(tint, size.minDimension * 0.08f, androidx.compose.ui.geometry.Offset(cx, t + h * 0.27f))
                drawCircle(tint, size.minDimension * 0.05f, androidx.compose.ui.geometry.Offset(cx - size.minDimension * 0.10f, t + h * 0.50f))
                drawCircle(tint, size.minDimension * 0.05f, androidx.compose.ui.geometry.Offset(cx + size.minDimension * 0.10f, t + h * 0.50f))
                drawCircle(tint, size.minDimension * 0.05f, androidx.compose.ui.geometry.Offset(cx, t + h * 0.65f))
            }
            Glyph.APPS -> {
                val cell = size.minDimension * 0.24f
                val gap = size.minDimension * 0.11f
                val sx = cx - cell - gap / 2f
                val sy = cy - cell - gap / 2f
                for (r in 0..1) for (col in 0..1) {
                    drawRoundRect(
                        tint,
                        topLeft = androidx.compose.ui.geometry.Offset(sx + col * (cell + gap), sy + r * (cell + gap)),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.2f)
                    )
                }
            }
            Glyph.CAST -> {
                drawRoundRect(
                    tint,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.18f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.50f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                )
                drawArc(
                    tint, 0f, 90f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.10f, size.height * 0.55f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.60f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round)
                )
                drawCircle(tint, stroke * 0.75f, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.80f))
            }
            Glyph.SETTINGS -> {
                val r = size.minDimension * 0.26f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val ix = cx + kotlin.math.cos(a).toFloat() * r * 1.35f
                    val iy = cy + kotlin.math.sin(a).toFloat() * r * 1.35f
                    val ox = cx + kotlin.math.cos(a).toFloat() * r * 1.75f
                    val oy = cy + kotlin.math.sin(a).toFloat() * r * 1.75f
                    drawLine(tint, androidx.compose.ui.geometry.Offset(ix, iy), androidx.compose.ui.geometry.Offset(ox, oy), strokeWidth = stroke * 1.2f, cap = StrokeCap.Square)
                }
                drawCircle(tint, r * 1.05f, androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 1.2f))
                drawCircle(tint, r * 0.34f, androidx.compose.ui.geometry.Offset(cx, cy))
            }
            Glyph.SPEAKER -> {
                val t = size.height * 0.34f
                val h = size.height * 0.32f
                drawRoundRect(
                    tint,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, t),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.18f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f)
                )
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.34f, t)
                    lineTo(size.width * 0.60f, size.height * 0.20f)
                    lineTo(size.width * 0.60f, size.height * 0.80f)
                    lineTo(size.width * 0.34f, t + h)
                    close()
                }
                drawPath(p, tint)
                drawArc(
                    tint, -48f, 96f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.22f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.62f, size.height * 0.56f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Glyph.PLAY_PAUSE -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.24f)
                    lineTo(size.width * 0.18f, size.height * 0.76f)
                    lineTo(size.width * 0.46f, cy)
                    close()
                }
                drawPath(p, tint)
                drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.30f), androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.30f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            Glyph.BACK -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.78f, size.height * 0.30f)
                    cubicTo(size.width * 0.42f, size.height * 0.20f, size.width * 0.22f, size.height * 0.34f, size.width * 0.22f, size.height * 0.56f)
                    cubicTo(size.width * 0.22f, size.height * 0.74f, size.width * 0.38f, size.height * 0.80f, size.width * 0.60f, size.height * 0.80f)
                    moveTo(size.width * 0.22f, size.height * 0.56f)
                    lineTo(size.width * 0.40f, size.height * 0.40f)
                    moveTo(size.width * 0.22f, size.height * 0.56f)
                    lineTo(size.width * 0.40f, size.height * 0.70f)
                }
                drawPath(
                    p, tint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    modifier: Modifier,
    remote: RemoteUiActions,
    onShowNumberPad: () -> Unit
) {
    Surface(
        modifier = modifier.size(114.dp, 33.dp),
        color = remoteSurface2(),
        shape = RoundedCornerShape(16.5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReferenceQuickActionSegment("â¹", "Back", remote::back)
            ReferenceQuickActionSegment("â", "Home", remote::home)
            ReferenceQuickActionSegment("123", "Numeric keypad", onShowNumberPad, small = true)
        }
    }
}

@Composable
private fun ReferenceQuickActionSegment(
    icon: String,
    description: String,
    onClick: () -> Unit,
    small: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) remotePressedSurface() else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            icon,
            color = remoteText(),
            fontSize = if (small) 8.sp else 15.sp,
            fontWeight = if (small) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun NumberPadOverlay(
    remote: RemoteUiActions,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onClose)
            .semantics { contentDescription = "Numeric keypad" }
    ) {
        Surface(
            modifier = Modifier
                .offset(55.dp, 275.dp)
                .size(190.dp, 260.dp)
                .clickable(onClick = { })
                .semantics { contentDescription = "Numeric keypad panel" },
            color = remoteSurface(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, remoteBorder())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Numeric keypad",
                        color = remoteText(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose)
                            .semantics { contentDescription = "Close numeric keypad" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", color = remoteText(), fontSize = 17.sp)
                    }
                }

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { key ->
                            NumberPadKey(
                                text = key,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (key) {
                                        "*" -> remote.star()
                                        "#" -> remote.pound()
                                        else -> remote.number(key.toInt())
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberPadKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) remotePressedSurface() else remoteSurface2())
            .border(1.dp, remoteBorder(), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = "Key " + text },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = remoteText(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
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
private fun DualRocker(
    modifier: Modifier,
    label: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    upIcon: String = "+",
    downIcon: String = "−"
) {
    Surface(
        modifier = modifier.size(46.dp, 101.dp),
        color = remoteSurface2(),
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, remoteBorder())
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RockerAction(
                contentDescription = "$label up",
                onClick = onUp,
                icon = upIcon
            )
            Text(
                label,
                color = remoteMuted(),
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold
            )
            RockerAction(
                contentDescription = "$label down",
                onClick = onDown,
                icon = downIcon
            )
        }
    }
}

@Composable
private fun RockerAction(
    contentDescription: String,
    onClick: () -> Unit,
    icon: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(icon, color = remoteText(), fontSize = 13.sp)
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
    Box(
        modifier
            .size(46.dp, 101.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(remoteSurface2())
            .border(1.dp, remoteBorder(), RoundedCornerShape(23.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
        ) {
            RockerGlyph(
                text = top,
                contentDescription = (if (label == "VOL") "Volume" else "Channel") + " up",
                onClick = onTop
            )
            RockerGlyph(
                text = middle,
                contentDescription = if (label == "VOL") "Mute" else "Channel menu",
                onClick = onMiddle
            )
            RockerBottomGlyph(
                text = bottom,
                label = label,
                contentDescription = (if (label == "VOL") "Volume" else "Channel") + " down",
                onClick = onBottom
            )
        }
    }
}

@Composable
private fun RockerGlyph(
    text: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    RockerSegmentContainer(contentDescription, onClick) {
        Text(text, color = remoteText(), fontSize = 13.sp)
    }
}

@Composable
private fun RockerBottomGlyph(
    text: String,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    RockerSegmentContainer(contentDescription, onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text, color = remoteText(), fontSize = 13.sp)
            Text(
                label,
                color = remoteMuted(),
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RockerSegmentContainer(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RemoteUiSpec.rockerSegmentHeightDp.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) remotePressedSurface() else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}


@Composable
private fun CompactNavControl(
    x: Int,
    y: Int,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(36.dp)
            .semantics { this.contentDescription = contentDescription },
        color = remoteSurface2(),
        shape = CircleShape,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun MiniControl(x: Int, y: Int, label: String, onClick: () -> Unit) {
    ControlSurface(
        x = x,
        y = y,
        w = 46,
        h = 46,
        radius = 10,
        color = remoteSurface2(),
        onClick = onClick,
        contentDescription = label
    ) {
        when (label) {
            "HOME" -> ReferenceGlyph(Glyph.HOME, remoteText(), Modifier.size(18.dp))
            "PLAY" -> ReferenceGlyph(Glyph.PLAY_PAUSE, remoteText(), Modifier.size(18.dp))
            else -> Text(label, color = remoteText(), fontSize = 13.sp)
        }
    }
}

@Composable
private fun WideControl(x: Int, y: Int, label: String, onClick: () -> Unit) {
    ControlSurface(
        x, y, 101, 46, 23, remoteSurface2(), onClick, contentDescription = label
    ) {
        if (label == "BACK") {
            ReferenceGlyph(Glyph.BACK, remoteText(), Modifier.size(22.dp))
        } else {
            Text(label, color = remoteMuted(), fontSize = 8.sp, fontWeight = FontWeight.Medium)
        }
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
            .size(70.dp, 32.dp)
            .semantics { contentDescription = text.removePrefix("â¶ ").trim() },
        color = remoteSurface2(),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                text.equals("YouTube", ignoreCase = true) -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(14.dp, 10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFFF0000)),
                        contentAlignment = Alignment.Center
                    ) { Text("â¶", color = Color.White, fontSize = 6.sp) }
                    Text("YouTube", modifier = Modifier.padding(start = 2.dp), color = textColor, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                }
                text == "NETFLIX" -> Text("NETFLIX", color = Color(0xFFE50914), fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                text == "Disney+" -> Text("Disney+", color = Color(0xFF6B63D9), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("prime video", color = remoteText(), fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
                    Canvas(
                        Modifier
                            .padding(top = 0.dp)
                            .width(25.dp)
                            .height(4.dp)
                    ) {
                        drawArc(
                            color = Color(0xFF00A8E1),
                            startAngle = 10f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(1f, -2f),
                            size = androidx.compose.ui.geometry.Size(size.width - 2f, size.height * 2.4f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceBottomNav(
    destination: String,
    onDestination: (Destination) -> Unit
) {
    val items = listOf(
        Triple(Destination.REMOTE, "Remote", Glyph.REMOTE),
        Triple(Destination.APPS, "Apps", Glyph.APPS),
        Triple(Destination.CAST, "Cast", Glyph.CAST),
        Triple(Destination.SETTINGS, "Settings", Glyph.SETTINGS)
    )
    val selectedTint = Color(0xFF198BFF)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = Modifier
                .offset(30.dp, 560.dp)
                .size(240.dp, 48.dp),
            color = remoteSurface2(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, remoteBorder())
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (item, label, glyph) ->
                    val selected = destination == item.name
                    Column(
                        modifier = Modifier
                            .width(54.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selected) {
                                    if (isSystemInDarkTheme()) Color(0xFF14324A) else Color(0xFFE1F0FF)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onDestination(item) }
                            .semantics { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ReferenceGlyph(
                            kind = glyph,
                            tint = if (selected) selectedTint else remoteText(),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            label,
                            color = if (selected) selectedTint else remoteText(),
                            fontSize = 6.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
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
private fun SettingsPage(tvName: String, status: String, onDisconnect: () -> Unit) {
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
                Text(tvName.ifBlank { "TV" }, color = remoteText(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(
                    status,
                    color = if (status == "متصل" || status == "Connected") Color(0xFF4ED17B) else remoteMuted(),
                    fontSize = 10.sp
                )
            }
        }
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .semantics { contentDescription = "Disconnect TV" },
            border = BorderStroke(1.dp, remoteBorder()),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Disconnect TV", fontSize = 10.sp)
        }
    }
}
