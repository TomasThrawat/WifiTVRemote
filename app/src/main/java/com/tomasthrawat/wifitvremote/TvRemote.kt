package com.tomasthrawat.wifitvremote

import android.os.Build
import remote.RemoteConfigure
import remote.RemoteDeviceInfo
import remote.RemoteKeyCode
import remote.RemoteKeyInject
import remote.RemoteMessage
import remote.RemotePingResponse
import remote.RemoteSetActive
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket

class TvRemote(
    private val host: String,
    private val id: ClientIdentity,
    private val onReady: () -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private var socket: SSLSocket? = null

    fun start() {
        AppLogger.i("REMOTE", "start host=" + host + " port=6466")
        Thread {
            try {
                socket = Tls.context(id).socketFactory.createSocket() as SSLSocket
                AppLogger.i("REMOTE", "connecting TLS port=6466 timeoutMs=8000")
                socket!!.connect(InetSocketAddress(host, 6466), 8000)
                socket!!.useClientMode = true
                AppLogger.i("REMOTE", "TLS handshake starting")
                socket!!.startHandshake()
                AppLogger.i("REMOTE", "TLS handshake complete protocol=" + socket!!.session.protocol + " cipher=" + socket!!.session.cipherSuite)
                AppLogger.d("REMOTE", "sending RemoteConfigure")
                send(config())
                while (true) {
                    val frame = Framing.read(socket!!.inputStream)
                    AppLogger.d("REMOTE", "received frame bytes=" + frame.size)
                    when (val message = RemoteMessage.parseFrom(frame)) {
                        else -> {
                            when {
                                message.hasRemoteConfigure() -> {
                                    AppLogger.i("REMOTE", "received RemoteConfigure; activating remote")
                                    send(
                                        RemoteMessage.newBuilder()
                                            .setRemoteSetActive(
                                                RemoteSetActive.newBuilder().setActive(622)
                                            )
                                            .build().toByteArray()
                                    )
                                    AppLogger.i("REMOTE", "remote active; onReady")
                                    onReady()
                                }
                                message.hasRemotePingRequest() -> {
                                    AppLogger.d("REMOTE", "received ping request; replying")
                                    send(
                                        RemoteMessage.newBuilder()
                                            .setRemotePingResponse(
                                                RemotePingResponse.newBuilder()
                                                    .setVal1(message.remotePingRequest.val1)
                                            )
                                            .build().toByteArray()
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.e("REMOTE", "remote thread failed", t)
                onError(t)
            }
        }.start()
    }

    private fun config() = RemoteMessage.newBuilder()
        .setRemoteConfigure(
            RemoteConfigure.newBuilder()
                .setCode1(622)
                .setDeviceInfo(
                    RemoteDeviceInfo.newBuilder()
                        .setModel(Build.MODEL)
                        .setVendor(Build.MANUFACTURER)
                        .setUnknown1(1)
                        .setUnknown2("1")
                        .setPackageName("androidtv-remote")
                        .setAppVersion("1.0.0")
                )
        )
        .build().toByteArray()

    fun key(key: RemoteKeyCode.KeyCode) {
        AppLogger.i("REMOTE_KEY", "sending key=" + key.name + " number=" + key.number)
        send(
            RemoteMessage.newBuilder()
                .setRemoteKeyInject(
                    RemoteKeyInject.newBuilder()
                        .setKeyCode(key.number)
                        .setDirection(RemoteKeyInject.Direction.SHORT)
                )
                .build().toByteArray()
        )
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
        try { socket?.close() } catch (_: Throwable) {}
    }

    private fun send(bytes: ByteArray) {
        synchronized(this) {
            AppLogger.d("REMOTE_IO", "send frame bytes=" + bytes.size)
            Framing.write(socket!!.outputStream, bytes)
        }
    }
}
