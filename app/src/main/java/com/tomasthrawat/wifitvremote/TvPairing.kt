package com.tomasthrawat.wifitvremote

import android.os.Handler
import android.os.Looper

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
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TvPairing(
    private val context: Context,
    private val host: String,
    private val onCode: () -> Unit,
    private val onPaired: (ClientIdentity) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private var socket: SSLSocket? = null
    private var clientIdentity: ClientIdentity? = null
    private var serverCertificate: X509Certificate? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun postMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    fun start() {
        AppLogger.i("PAIRING", "start host=" + host + " port=6467")
        Thread {
            try {
                val id = CertificateStore.loadOrCreate(context, host)
                clientIdentity = id
                AppLogger.i("PAIRING", "client identity loaded")

                val s = Tls.context(id).socketFactory.createSocket() as SSLSocket
                socket = s
                AppLogger.i("PAIRING", "connecting TLS port=6467 timeoutMs=8000")
                s.connect(InetSocketAddress(host, 6467), 8000)
                s.useClientMode = true
                AppLogger.i("PAIRING", "TLS handshake starting")
                s.startHandshake()
                AppLogger.i("PAIRING", "TLS handshake complete protocol=" + s.session.protocol + " cipher=" + s.session.cipherSuite)

                serverCertificate = s.session.peerCertificates.firstOrNull() as? X509Certificate
                    ?: throw IllegalStateException("TV did not provide an X.509 certificate")
                AppLogger.i("PAIRING", "server certificate received algorithm=" + serverCertificate!!.publicKey.algorithm)

                AppLogger.d("PAIRING", "sending PairingRequest")
                send(request())

                while (true) {
                    val frame = Framing.read(s.inputStream)
                    AppLogger.d("PAIRING", "received frame bytes=" + frame.size)
                    val message = PairingMessage.parseFrom(frame)
                    AppLogger.d("PAIRING", "status=" + message.status + " requestAck=" + message.hasPairingRequestAck() + " option=" + message.hasPairingOption() + " configAck=" + message.hasPairingConfigurationAck() + " secretAck=" + message.hasPairingSecretAck())

                    when {
                        message.status == PairingMessage.Status.STATUS_BAD_SECRET ->
                            throw IllegalStateException("TV rejected the pairing secret")

                        message.status != PairingMessage.Status.STATUS_OK ->
                            throw IllegalStateException("TV status: " + message.status)

                        message.hasPairingRequestAck() -> { AppLogger.d("PAIRING", "request acknowledged; sending option"); send(option()) }
                        message.hasPairingOption() -> { AppLogger.d("PAIRING", "option received; sending configuration"); send(config()) }
                        message.hasPairingConfigurationAck() -> { AppLogger.i("PAIRING", "configuration acknowledged; waiting for user code"); postMain(onCode) }

                        message.hasPairingSecretAck() -> {
                            AppLogger.i("PAIRING", "secret acknowledged; pairing successful")
                            s.close()
                            postMain { onPaired(id) }
                            return@Thread
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.e("PAIRING", "pairing thread failed", t)
                postMain { onError(t) }
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

    suspend fun submitCode(raw: String): Boolean = withContext(Dispatchers.IO) {
        AppLogger.i("PAIRING_CODE", "submit requested rawLength=" + raw.trim().length)
        try {
            val id = clientIdentity ?: return false
            val server = serverCertificate ?: return false
            val clientKey = id.cert.publicKey as? RSAPublicKey
                ?: throw IllegalStateException("Client certificate is not RSA")
            val serverKey = server.publicKey as? RSAPublicKey
                ?: throw IllegalStateException("TV certificate is not RSA")

            val code = raw.trim()
                .removePrefix("0x")
                .removePrefix("0X")
                .uppercase()

            if (code.length != 6 || code.any { it !in "0123456789ABCDEF" }) {
                AppLogger.w("PAIRING_CODE", "rejected locally: invalid hexadecimal length=" + code.length)
                return false
            }

            fun unsigned(n: BigInteger): ByteArray {
                val bytes = n.abs().toByteArray()
                return if (bytes.size > 1 && bytes[0].toInt() == 0) {
                    bytes.copyOfRange(1, bytes.size)
                } else {
                    bytes
                }
            }

            fun exponentBytes(key: RSAPublicKey): ByteArray {
                val exponent = unsigned(key.publicExponent)
                return exponent
            }

            val pinBytes = code.substring(2)
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(unsigned(clientKey.modulus))
            digest.update(exponentBytes(clientKey))
            digest.update(unsigned(serverKey.modulus))
            digest.update(exponentBytes(serverKey))
            digest.update(pinBytes)
            val secret = digest.digest()
            AppLogger.d("PAIRING_CODE", "pairing hash computed bytes=" + secret.size)

            val expectedFirstByte = code.substring(0, 2).toInt(16)
            if ((secret[0].toInt() and 0xFF) != expectedFirstByte) {
                AppLogger.w("PAIRING_CODE", "hash mismatch expectedPrefix=" + code.substring(0, 2) + " actualPrefix=" + String.format("%02X", secret[0].toInt() and 0xFF))
                throw IllegalArgumentException("Pairing code does not match the TLS certificates")
            }

            AppLogger.i("PAIRING_CODE", "hash verified; sending PairingSecret bytes=" + secret.size)
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
            AppLogger.e("PAIRING_CODE", "submit failed", t)
            postMain { onError(t) }
            false
        }
    }

    fun stop() {
        AppLogger.i("PAIRING", "stop requested")
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
    }
}
