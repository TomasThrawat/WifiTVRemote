package com.tomasthrawat.wifitvremote

import android.os.Build
import android.os.Handler
import android.os.Looper
import remote.RemoteConfigure
import remote.RemoteDeviceInfo
import remote.RemoteEditInfo
import remote.RemoteImeBatchEdit
import remote.RemoteImeKeyInject
import remote.RemoteImeObject
import remote.RemoteKeyCode
import remote.RemoteKeyInject
import remote.RemoteMessage
import remote.RemotePingResponse
import remote.RemoteSetActive
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TvRemote(
    private val host: String,
    private val id: ClientIdentity,
    private val onReady: () -> Unit,
    private val onError: (Throwable) -> Unit,
    onTextStateChanged: (Boolean) -> Unit = {}
) {
    companion object { private const val REQUESTED_FEATURES = 622 }

    private var textStateListener: ((Boolean) -> Unit)? = onTextStateChanged
    private var socket: SSLSocket? = null
    private var activeFeatures = REQUESTED_FEATURES
    private var handshakeReady = false
    private var configureSent = false
    private var activeSent = false
    private var imeCounter = 0
    private var fieldCounter = 0
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setTextStateListener(listener: ((Boolean) -> Unit)?) {
        textStateListener = listener
    }

    private fun postMain(block: () -> Unit) = mainHandler.post(block)

    fun start() {
        AppLogger.i("TvRemote", "Starting remote host=" + host + " port=6466")
        ioScope.launch {
            try {
                socket = Tls.context(id).socketFactory.createSocket() as SSLSocket
                AppLogger.d("TvRemote", "Connecting TCP host=" + host)
                socket!!.connect(InetSocketAddress(host, 6466), 8000)
                AppLogger.i("TvRemote", "TCP connected host=" + host)
                socket!!.useClientMode = true
                socket!!.startHandshake()
                AppLogger.i("TvRemote", "TLS handshake complete host=" + host)
                val configurePayload = config(REQUESTED_FEATURES)
                AppLogger.d(
                    "TvRemote",
                    "Sending RemoteConfigure features=" + REQUESTED_FEATURES + " bytes=" + configurePayload.size
                )
                send(configurePayload)
                configureSent = true
                while (true) {
                    val frame = Framing.read(socket!!.inputStream)
                    val message = RemoteMessage.parseFrom(frame)
                    AppLogger.d(
                        "TvRemote",
                        "Received remote frame bytes=" + frame.size + " type=" + remoteMessageType(message)
                    )
                    when {
                        message.hasRemoteConfigure() -> {
                            activeFeatures = REQUESTED_FEATURES and message.remoteConfigure.code1
                            AppLogger.i(
                                "TvRemote",
                                "RemoteConfigure code1=" + message.remoteConfigure.code1 +
                                    "; activeFeatures=" + activeFeatures
                            )
                            if (configureSent && !activeSent) {
                                val activePayload = RemoteMessage.newBuilder().setRemoteSetActive(
                                    RemoteSetActive.newBuilder().setActive(activeFeatures)
                                ).build().toByteArray()
                                AppLogger.d(
                                    "TvRemote",
                                    "Sending RemoteSetActive activeFeatures=" + activeFeatures +
                                        " bytes=" + activePayload.size
                                )
                                send(activePayload)
                                activeSent = true
                            }
                        }
                        message.hasRemoteSetActive() -> {
                            handshakeReady = true
                            AppLogger.i("TvRemote", "RemoteSetActive received; connection ready")
                            postMain(onReady)
                        }
                        message.hasRemoteStart() -> {
                            if (activeSent && !handshakeReady) {
                                handshakeReady = true
                                AppLogger.i("TvRemote", "RemoteStart received; connection ready")
                                postMain(onReady)
                            }
                        }
                        message.hasRemotePingRequest() -> {
                            AppLogger.v("TvRemote", "Received RemotePingRequest val1=" + message.remotePingRequest.val1)
                            val pingPayload = RemoteMessage.newBuilder().setRemotePingResponse(
                                RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1)
                            ).build().toByteArray()
                            AppLogger.v("TvRemote", "Sending RemotePingResponse bytes=" + pingPayload.size)
                            send(pingPayload)
                        }
                        message.hasRemoteImeBatchEdit() -> {
                            val edit = message.remoteImeBatchEdit
                            imeCounter = edit.imeCounter
                            fieldCounter = edit.fieldCounter
                            AppLogger.d(
                                "TvRemote",
                                "IME batch edit active imeCounter=" + imeCounter + " fieldCounter=" + fieldCounter
                            )
                            postMain { textStateListener?.invoke(true) }
                        }
                        message.hasRemoteImeKeyInject() -> {
                            val state = message.remoteImeKeyInject
                            if (state.hasTextFieldStatus()) {
                                fieldCounter = state.textFieldStatus.counterField
                                AppLogger.d(
                                    "TvRemote",
                                    "IME key inject reports active text field counter=" + fieldCounter
                                )
                                postMain { textStateListener?.invoke(true) }
                            }
                        }
                        message.hasRemoteImeShowRequest() -> {
                            val state = message.remoteImeShowRequest
                            if (state.hasRemoteTextFieldStatus()) {
                                fieldCounter = state.remoteTextFieldStatus.counterField
                                AppLogger.d(
                                    "TvRemote",
                                    "IME show request reports active text field counter=" + fieldCounter
                                )
                                postMain { textStateListener?.invoke(true) }
                            }
                        }
                        message.hasRemoteError() -> {
                            val error = IllegalStateException("TV returned a remote protocol error")
                            AppLogger.e("TvRemote", "TV returned RemoteError", error)
                            postMain { onError(error) }
                        }
                    }
                }
            } catch (e: java.io.EOFException) {
                // The TV can close the remote channel cleanly after a completed handshake.
                // Do not surface that normal stream termination as a protocol error.
                if (handshakeReady) {
                    handshakeReady = false
                    AppLogger.w("TvRemote", "EOF after completed handshake; channel closed by TV", e)
                    try { socket?.close() } catch (closeError: Throwable) {
                        AppLogger.w("TvRemote", "Socket close after EOF failed", closeError)
                    }
                } else {
                    AppLogger.e("TvRemote", "EOF before handshake completed", e)
                    postMain { onError(e) }
                }
            } catch (t: Throwable) {
                AppLogger.e("TvRemote", "Remote loop failed", t)
                postMain { onError(t) }
            }
        }
    }

    private fun config(features: Int) = RemoteMessage.newBuilder().setRemoteConfigure(
        RemoteConfigure.newBuilder().setCode1(features).setDeviceInfo(
            RemoteDeviceInfo.newBuilder()
                .setModel(Build.MODEL).setVendor(Build.MANUFACTURER)
                .setUnknown1(1).setUnknown2("1")
                .setPackageName("atvremote2").setAppVersion("1.0.0")
        )
    ).build().toByteArray()

    fun key(key: RemoteKeyCode.KeyCode) {
        ioScope.launch {
            try {
                if (!handshakeReady) {
                    AppLogger.w("TvRemote", "Ignoring key=" + key.name + " because connection is not ready")
                    return@launch
                }
                AppLogger.i("TvRemote", "Sending key=" + key.name)
                send(RemoteMessage.newBuilder().setRemoteKeyInject(
                    RemoteKeyInject.newBuilder().setKeyCode(key.number)
                        .setDirection(RemoteKeyInject.Direction.SHORT)
                ).build().toByteArray())
            } catch (t: Throwable) {
                AppLogger.e("TvRemote", "Key send failed key=" + key.name, t)
                postMain { onError(t) }
            }
        }
    }

    fun sendText(text: String) {
        ioScope.launch {
            try {
                if (!handshakeReady) {
                    AppLogger.w("TvRemote", "Ignoring text send because connection is not ready")
                    return@launch
                }
                AppLogger.i(
                    "TvRemote",
                    "Sending text length=" + text.length +
                        " imeCounter=" + imeCounter +
                        " fieldCounter=" + fieldCounter
                )
                val position = text.length.coerceAtLeast(1) - 1
                send(RemoteMessage.newBuilder().setRemoteImeBatchEdit(
                    RemoteImeBatchEdit.newBuilder()
                        .setImeCounter(imeCounter)
                        .setFieldCounter(fieldCounter)
                        .addEditInfo(RemoteEditInfo.newBuilder().setInsert(1).setTextFieldStatus(
                            RemoteImeObject.newBuilder().setStart(position).setEnd(position).setValue(text)
                        ))
                ).build().toByteArray())
            } catch (t: Throwable) {
                AppLogger.e("TvRemote", "Text send failed length=" + text.length, t)
                postMain { onError(t) }
            }
        }
    }

    fun clearText() {
        AppLogger.i("TvRemote", "Clearing active TV text field")
        sendText("")
    }
    fun power() = key(RemoteKeyCode.KeyCode.KEYCODE_POWER)
    fun home() = key(RemoteKeyCode.KeyCode.KEYCODE_HOME)
    fun back() = key(RemoteKeyCode.KeyCode.KEYCODE_BACK)
    fun up() = key(RemoteKeyCode.KeyCode.KEYCODE_DPAD_UP)
    fun down() = key(RemoteKeyCode.KeyCode.KEYCODE_DPAD_DOWN)
    fun left() = key(RemoteKeyCode.KeyCode.KEYCODE_DPAD_LEFT)
    fun right() = key(RemoteKeyCode.KeyCode.KEYCODE_DPAD_RIGHT)
    fun ok() = key(RemoteKeyCode.KeyCode.KEYCODE_DPAD_CENTER)
    fun volumeUp() = key(RemoteKeyCode.KeyCode.KEYCODE_VOLUME_UP)
    fun volumeDown() = key(RemoteKeyCode.KeyCode.KEYCODE_VOLUME_DOWN)
    fun mute() = key(RemoteKeyCode.KeyCode.KEYCODE_MUTE)
    fun playPause() = key(RemoteKeyCode.KeyCode.KEYCODE_MEDIA_PLAY_PAUSE)
    fun enter() = key(RemoteKeyCode.KeyCode.KEYCODE_ENTER)
    fun delete() = key(RemoteKeyCode.KeyCode.KEYCODE_DEL)
    fun space() = key(RemoteKeyCode.KeyCode.KEYCODE_SPACE)

    fun stop() {
        AppLogger.i("TvRemote", "Stopping remote host=" + host)
        ioScope.cancel()
        try { socket?.close() } catch (t: Throwable) {
            AppLogger.w("TvRemote", "Socket close failed during stop", t)
        }
    }

    private fun send(bytes: ByteArray) {
        synchronized(this) {
            Framing.write(socket!!.outputStream, bytes)
        }
    }

    private fun remoteMessageType(message: RemoteMessage): String = when {
        message.hasRemoteConfigure() -> "RemoteConfigure"
        message.hasRemoteSetActive() -> "RemoteSetActive"
        message.hasRemoteStart() -> "RemoteStart"
        message.hasRemotePingRequest() -> "RemotePingRequest"
        message.hasRemotePingResponse() -> "RemotePingResponse"
        message.hasRemoteImeBatchEdit() -> "RemoteImeBatchEdit"
        message.hasRemoteImeKeyInject() -> "RemoteImeKeyInject"
        message.hasRemoteImeShowRequest() -> "RemoteImeShowRequest"
        message.hasRemoteError() -> "RemoteError"
        message.hasRemoteKeyInject() -> "RemoteKeyInject"
        else -> "Other"
    }
}