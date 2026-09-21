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
import remote.RemoteTextFieldStatus
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
        ioScope.launch {
            try {
                socket = Tls.context(id).socketFactory.createSocket() as SSLSocket
                socket!!.connect(InetSocketAddress(host, 6466), 8000)
                socket!!.useClientMode = true
                socket!!.startHandshake()
                send(config(REQUESTED_FEATURES))
                configureSent = true
                while (true) {
                    val message = RemoteMessage.parseFrom(Framing.read(socket!!.inputStream))
                    when {
                        message.hasRemoteConfigure() -> {
                            activeFeatures = REQUESTED_FEATURES and message.remoteConfigure.code1
                            if (configureSent && !activeSent) {
                                send(RemoteMessage.newBuilder().setRemoteSetActive(
                                    RemoteSetActive.newBuilder().setActive(activeFeatures)
                                ).build().toByteArray())
                                activeSent = true
                            }
                        }
                        message.hasRemoteSetActive() -> {
                            handshakeReady = true
                            postMain(onReady)
                        }
                        message.hasRemoteStart() -> {
                            if (activeSent && !handshakeReady) {
                                handshakeReady = true
                                postMain(onReady)
                            }
                        }
                        message.hasRemotePingRequest() -> {
                            send(RemoteMessage.newBuilder().setRemotePingResponse(
                                RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1)
                            ).build().toByteArray())
                        }
                        message.hasRemoteImeBatchEdit() -> {
                            val edit = message.remoteImeBatchEdit
                            imeCounter = edit.imeCounter
                            fieldCounter = edit.fieldCounter
                            postMain { textStateListener?.invoke(true) }
                        }
                        message.hasRemoteImeKeyInject() -> {
                            val state = message.remoteImeKeyInject
                            if (state.hasTextFieldStatus()) {
                                fieldCounter = state.textFieldStatus.counterField
                                postMain { textStateListener?.invoke(true) }
                            }
                        }
                        message.hasRemoteImeShowRequest() -> {
                            val state = message.remoteImeShowRequest
                            if (state.hasRemoteTextFieldStatus()) {
                                fieldCounter = state.remoteTextFieldStatus.counterField
                                postMain { textStateListener?.invoke(true) }
                            }
                        }
                        message.hasRemoteError() -> {
                            postMain { onError(IllegalStateException("TV returned a remote protocol error")) }
                        }
                    }
                }
            } catch (t: Throwable) {
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
                if (!handshakeReady) return@launch
                send(RemoteMessage.newBuilder().setRemoteKeyInject(
                    RemoteKeyInject.newBuilder().setKeyCode(key.number)
                        .setDirection(RemoteKeyInject.Direction.SHORT)
                ).build().toByteArray())
            } catch (t: Throwable) { postMain { onError(t) } }
        }
    }

    fun sendText(text: String) {
        ioScope.launch {
            try {
                if (!handshakeReady) return@launch
                val position = text.length.coerceAtLeast(1) - 1
                send(RemoteMessage.newBuilder().setRemoteImeBatchEdit(
                    RemoteImeBatchEdit.newBuilder()
                        .setImeCounter(imeCounter)
                        .setFieldCounter(fieldCounter)
                        .addEditInfo(RemoteEditInfo.newBuilder().setInsert(1).setTextFieldStatus(
                            RemoteImeObject.newBuilder().setStart(position).setEnd(position).setValue(text)
                        ))
                ).build().toByteArray())
            } catch (t: Throwable) { postMain { onError(t) } }
        }
    }

    fun clearText() = sendText("")
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
        ioScope.cancel()
        try { socket?.close() } catch (_: Throwable) {}
    }

    private fun send(bytes: ByteArray) {
        synchronized(this) { Framing.write(socket!!.outputStream, bytes) }
    }
}