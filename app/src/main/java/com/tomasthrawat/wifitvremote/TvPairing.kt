package com.tomasthrawat.wifitvremote

import android.content.Context
import android.os.Build
import com.google.protobuf.ByteString
import pairing.PairingConfiguration
import pairing.PairingEncoding
import pairing.PairingMessage
import pairing.PairingOption
import pairing.PairingRequest
import pairing.PairingSecret
import pairing.RoleType
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class TvPairing(
    private val context: Context,
    private val host: String,
    private val onCode: () -> Unit,
    private val onPaired: (ClientIdentity) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private var socket: SSLSocket? = null

    fun start() {
        Thread {
            try {
                val id = CertificateStore.loadOrCreate(context, host)
                socket = Tls.context(id).socketFactory.createSocket() as SSLSocket
                socket!!.connect(InetSocketAddress(host, 6467), 8000)
                socket!!.useClientMode = true
                socket!!.startHandshake()
                send(request())

                while (true) {
                    val message = PairingMessage.parseFrom(Framing.read(socket!!.inputStream))
                    if (message.status != PairingMessage.Status.STATUS_OK) {
                        throw IllegalStateException("TV status: " + message.status)
                    }
                    when {
                        message.hasPairingRequestAck() -> send(option())
                        message.hasPairingOption() -> send(config())
                        message.hasPairingConfigurationAck() -> onCode()
                        message.hasPairingSecretAck() -> {
                            socket!!.close()
                            onPaired(id)
                            return@Thread
                        }
                    }
                }
            } catch (t: Throwable) {
                onError(t)
            }
        }.start()
    }

    private fun request() =
        PairingMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(PairingMessage.Status.STATUS_OK)
            .setPairingRequest(
                PairingRequest.newBuilder()
                    .setServiceName("androidtv-remote")
                    .setClientName(Build.MODEL.ifBlank { "Wi-Fi TV Remote" })
                    .build()
            )
            .build()
            .toByteArray()

    private fun option() =
        PairingMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(PairingMessage.Status.STATUS_OK)
            .setPairingOption(
                PairingOption.newBuilder()
                    .setPreferredRole(RoleType.ROLE_TYPE_INPUT)
                    .addInputEncodings(
                        PairingEncoding.newBuilder()
                            .setType(PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
                            .setSymbolLength(6)
                    )
                    .build()
            )
            .build()
            .toByteArray()

    private fun config() =
        PairingMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(PairingMessage.Status.STATUS_OK)
            .setPairingConfiguration(
                PairingConfiguration.newBuilder()
                    .setClientRole(RoleType.ROLE_TYPE_INPUT)
                    .setEncoding(
                        PairingEncoding.newBuilder()
                            .setType(PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
                            .setSymbolLength(6)
                    )
                    .build()
            )
            .build()
            .toByteArray()

    private fun send(bytes: ByteArray) {
        Framing.write(socket!!.outputStream, bytes)
    }

    fun submitCode(raw: String): Boolean {
        return try {
            val s = socket ?: return false
            val local = s.session.localCertificates.first() as java.security.cert.X509Certificate
            val server = s.session.peerCertificates.first() as java.security.cert.X509Certificate
            val clientKey = local.publicKey as RSAPublicKey
            val serverKey = server.publicKey as RSAPublicKey
            val code = raw.trim().removePrefix("0x").removePrefix("0X")
            if (code.length != 6 || code.any { it !in "0123456789abcdefABCDEF" }) return false

            fun unsigned(n: BigInteger): ByteArray {
                val bytes = n.toByteArray()
                return if (bytes.size > 1 && bytes[0].toInt() == 0) {
                    bytes.copyOfRange(1, bytes.size)
                } else {
                    bytes
                }
            }

            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(unsigned(clientKey.modulus))
            digest.update(unsigned(clientKey.publicExponent))
            digest.update(unsigned(serverKey.modulus))
            digest.update(unsigned(serverKey.publicExponent))
            digest.update(code.takeLast(4).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            val secret = digest.digest()

            send(
                PairingMessage.newBuilder()
                    .setProtocolVersion(2)
                    .setStatus(PairingMessage.Status.STATUS_OK)
                    .setPairingSecret(
                        PairingSecret.newBuilder()
                            .setSecret(ByteString.copyFrom(secret))
                            .build()
                    )
                    .build()
                    .toByteArray()
            )
            true
        } catch (t: Throwable) {
            onError(t)
            false
        }
    }

    fun stop() {
        try { socket?.close() } catch (_: Throwable) {}
    }
}
