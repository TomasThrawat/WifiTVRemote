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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TvRemote(
    private val host: String,
    private val id: ClientIdentity,
    private val onReady: () -> Unit,
    private val onError: (Throwable) -> Unit,
    onTextStateChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val REQUESTED_FEATURES = 622
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 15_000L
    }

    private var textStateListener: ((Boolean) -> Unit)? = onTextStateChanged
    private var socket: SSLSocket? = null
    private var activeFeatures = REQUESTED_FEATURES
    @Volatile private var handshakeReady = false
    private var configureSent = false
    private var activeSent = false
    @Volatile private var stopped = false
    private var connectionJob: Job? = null
    private var imeCounter = 0
    private var fieldCounter = 0
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setTextStateListener(listener: ((Boolean) -> Unit)?) {
        textStateListener = listener
    }

    private fun postMain(block: () -> Unit) = mainHandler.post(block)

    @Synchronized
    fun start() {
        if (connectionJob?.isActive == true) {
            
            return
        }
        stopped = false
        connectionJob = ioScope.launch {
            runConnectionLoop()
        }
    }

    private suspend fun runConnectionLoop() {
        var reconnectAttempt = 0
        while (!stopped) {
            try {
                resetSessionState()
                closeSocketQuietly()

                
                socket = Tls.context(id).socketFactory.createSocket() as SSLSocket
                
                socket!!.connect(InetSocketAddress(host, 6466), 8000)
                
                socket!!.useClientMode = true
                socket!!.startHandshake()
                

                val configurePayload = config(REQUESTED_FEATURES)
                
                send(configurePayload)
                configureSent = true

                while (!stopped) {
                    val frame = Framing.read(socket!!.inputStream)
                    val message = RemoteMessage.parseFrom(frame)
                    
                    when {
                        message.hasRemoteConfigure() -> {
                            activeFeatures = REQUESTED_FEATURES and message.remoteConfigure.code1
                            
                            if (configureSent && !activeSent) {
                                val activePayload = RemoteMessage.newBuilder().setRemoteSetActive(
                                    RemoteSetActive.newBuilder().setActive(activeFeatures)
                                ).build().toByteArray()
                                
                                send(activePayload)
                                activeSent = true
                            }
                        }
                        message.hasRemoteSetActive() -> {
                            handshakeReady = true
                            
                            postMain(onReady)
                            reconnectAttempt = 0
                        }
                        message.hasRemoteStart() -> {
                            if (activeSent && !handshakeReady) {
                                handshakeReady = true
                                
                                postMain(onReady)
                                reconnectAttempt = 0
                            }
                        }
                        message.hasRemotePingRequest() -> {
                            
                            val pingPayload = RemoteMessage.newBuilder().setRemotePingResponse(
                                RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1)
                            ).build().toByteArray()
                            
                            send(pingPayload)
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
                            val error = IllegalStateException("TV returned a remote protocol error")
                            
                            postMain { onError(error) }
                        }
                    }
                }
                break
            } catch (e: java.io.EOFException) {
                if (stopped) break
                if (handshakeReady) {
                    
                } else {
                    
                }
            } catch (e: java.net.SocketException) {
                if (stopped) break
                if (handshakeReady) {
                    
                } else {
                    
                    postMain { onError(e) }
                    break
                }
            } catch (t: Throwable) {
                if (stopped) break
                
                postMain { onError(t) }
                break
            } finally {
                handshakeReady = false
                closeSocketQuietly()
            }

            if (stopped) break

            reconnectAttempt++
            if (reconnectAttempt > MAX_RECONNECT_ATTEMPTS) {
                val error = IllegalStateException("Remote connection lost after $MAX_RECONNECT_ATTEMPTS reconnect attempts")
                
                postMain { onError(error) }
                break
            }

            val delayMs = (INITIAL_RECONNECT_DELAY_MS shl (reconnectAttempt - 1).coerceAtMost(4))
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)
            
            delay(delayMs)
        }
    }

    private fun resetSessionState() {
        activeFeatures = REQUESTED_FEATURES
        handshakeReady = false
        configureSent = false
        activeSent = false
        imeCounter = 0
        fieldCounter = 0
    }

    private fun closeSocketQuietly() {
        val current = synchronized(this) {
            val value = socket
            socket = null
            value
        }
        try {
            current?.close()
        } catch (t: Throwable) {
            
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
                    
                    return@launch
                }
                
                send(RemoteMessage.newBuilder().setRemoteKeyInject(
                    RemoteKeyInject.newBuilder().setKeyCode(key.number)
                        .setDirection(RemoteKeyInject.Direction.SHORT)
                ).build().toByteArray())
            } catch (t: Throwable) {
                
                postMain { onError(t) }
            }
        }
    }

    fun sendText(text: String) {
        ioScope.launch {
            try {
                if (!handshakeReady) {
                    
                    return@launch
                }
                
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
                
                postMain { onError(t) }
            }
        }
    }

    fun clearText() {
        
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

    @Synchronized
    fun stop() {
        
        stopped = true
        connectionJob?.cancel()
        connectionJob = null
        ioScope.cancel()
        closeSocketQuietly()
    }

    private fun send(bytes: ByteArray) {
        synchronized(this) {
            val current = socket ?: throw java.io.IOException("Remote socket is not connected")
            Framing.write(current.outputStream, bytes)
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