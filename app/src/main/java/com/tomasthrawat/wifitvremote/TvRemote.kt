package com.tomasthrawat.wifitvremote

import android.os.Build
import android.os.Handler
import android.os.Looper
import remote.RemoteConfigure
import remote.RemoteDeviceInfo
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
    private val onError: (Throwable) -> Unit
) {
    companion object {
        private const val REQUESTED_FEATURES = 622
    }

    private var socket: SSLSocket? = null
    private var activeFeatures = REQUESTED_FEATURES
    private var handshakeReady = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun postMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    fun start() {
        AppLogger.i("REMOTE", "start host=" + host + " port=6466")
        ioScope.launch {
            try {
                socket = Tls.context(id).socketFactory.createSocket() as SSLSocket
                AppLogger.i("REMOTE", "connecting TLS port=6466 timeoutMs=8000")
                socket!!.connect(InetSocketAddress(host, 6466), 8000)
                socket!!.useClientMode = true
                AppLogger.i("REMOTE", "TLS handshake starting")
                socket!!.startHandshake()
                AppLogger.i(
                    "REMOTE",
                    "TLS handshake complete protocol=" + socket!!.session.protocol +
                        " cipher=" + socket!!.session.cipherSuite
                )

                AppLogger.i(
                    "REMOTE",
                    "waiting for TV RemoteConfigure before sending client configuration"
                )

                while (true) {
                    val frame = Framing.read(socket!!.inputStream)
                    AppLogger.d("REMOTE", "received frame bytes=" + frame.size)
                    val message = RemoteMessage.parseFrom(frame)

                    when {
                        message.hasRemoteConfigure() -> {
                            val supported = message.remoteConfigure.code1
                            activeFeatures = REQUESTED_FEATURES and supported
                            AppLogger.i(
                                "REMOTE",
                                "received TV RemoteConfigure supportedFeatures=" + supported +
                                    " requestedFeatures=" + REQUESTED_FEATURES +
                                    " negotiatedFeatures=" + activeFeatures
                            )
                            send(config(activeFeatures))
                            AppLogger.i(
                                "REMOTE",
                                "sent client RemoteConfigure negotiatedFeatures=" +
                                    activeFeatures
                            )
                        }

                        message.hasRemoteSetActive() -> {
                            AppLogger.i(
                                "REMOTE",
                                "received RemoteSetActive active=" +
                                    message.remoteSetActive.active +
                                    "; replying active=" + activeFeatures
                            )
                            send(
                                RemoteMessage.newBuilder()
                                    .setRemoteSetActive(
                                        RemoteSetActive.newBuilder()
                                            .setActive(activeFeatures)
                                    )
                                    .build()
                                    .toByteArray()
                            )
                            handshakeReady = true
                            AppLogger.i(
                                "REMOTE",
                                "remote handshake active; waiting for RemoteStart"
                            )
                        }

                        message.hasRemoteStart() -> {
                            AppLogger.i(
                                "REMOTE",
                                "received RemoteStart; remote connection ready"
                            )
                            if (handshakeReady) {
                                postMain(onReady)
                            }
                        }

                        message.hasRemotePingRequest() -> {
                            AppLogger.d(
                                "REMOTE",
                                "received ping request val1=" +
                                    message.remotePingRequest.val1
                            )
                            send(
                                RemoteMessage.newBuilder()
                                    .setRemotePingResponse(
                                        RemotePingResponse.newBuilder()
                                            .setVal1(message.remotePingRequest.val1)
                                    )
                                    .build()
                                    .toByteArray()
                            )
                        }

                        message.hasRemoteError() -> {
                            AppLogger.e(
                                "REMOTE",
                                "TV returned RemoteError=" + message.remoteError
                            )
                        }

                        message.hasRemoteAppLinkLaunchRequest() -> {
                            AppLogger.i("REMOTE", "received RemoteAppLinkLaunchRequest")
                        }

                        message.hasRemoteSetPreferredAudioDevice() -> {
                            AppLogger.i("REMOTE", "received RemoteSetPreferredAudioDevice")
                        }

                        else -> {
                            AppLogger.w(
                                "REMOTE",
                                "received unhandled RemoteMessage=" + message
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.e("REMOTE", "remote thread failed", t)
                postMain { onError(t) }
            }
        }
    }

    private fun config(features: Int) = RemoteMessage.newBuilder()
        .setRemoteConfigure(
            RemoteConfigure.newBuilder()
                .setCode1(features)
                .setDeviceInfo(
                    RemoteDeviceInfo.newBuilder()
                        .setModel(Build.MODEL)
                        .setVendor(Build.MANUFACTURER)
                        .setUnknown1(1)
                        .setUnknown2("1")
                        .setPackageName("atvremote2")
                        .setAppVersion("1.0.0")
                )
        )
        .build()
        .toByteArray()

    fun key(key: RemoteKeyCode.KeyCode) {
        ioScope.launch {
            try {
                if (!handshakeReady) {
                    AppLogger.w(
                        "REMOTE_KEY",
                        "ignoring key before remote handshake is ready key=" + key.name
                    )
                    return@launch
                }
                AppLogger.i(
                    "REMOTE_KEY",
                    "sending key=" + key.name + " number=" + key.number
                )
                send(
                    RemoteMessage.newBuilder()
                        .setRemoteKeyInject(
                            RemoteKeyInject.newBuilder()
                                .setKeyCode(key.number)
                                .setDirection(RemoteKeyInject.Direction.SHORT)
                        )
                        .build()
                        .toByteArray()
                )
            } catch (t: Throwable) {
                AppLogger.e("REMOTE_KEY", "key send failed", t)
                postMain { onError(t) }
            }
        }
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

    fun stop() {
        AppLogger.i("REMOTE", "stop requested")
        ioScope.cancel()
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
    }

    private fun send(bytes: ByteArray) {
        synchronized(this) {
            AppLogger.d("REMOTE_IO", "send frame bytes=" + bytes.size)
            Framing.write(socket!!.outputStream, bytes)
        }
    }
}
