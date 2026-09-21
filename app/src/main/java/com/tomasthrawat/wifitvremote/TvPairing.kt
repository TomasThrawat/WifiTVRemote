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
        AppLogger.i("TvPairing", "Starting pairing host=" + host + " port=6467")
        Thread {
            try {
                val id = CertificateStore.loadOrCreate(context, host)
                clientIdentity = id

                val s = Tls.context(id).socketFactory.createSocket() as SSLSocket
                socket = s
                AppLogger.d("TvPairing", "Opening TLS socket host=" + host)
                s.connect(InetSocketAddress(host, 6467), 8000)
                AppLogger.i("TvPairing", "TCP connected host=" + host)
                s.useClientMode = true
                s.startHandshake()
                AppLogger.i("TvPairing", "TLS handshake complete host=" + host)

                serverCertificate = s.session.peerCertificates.firstOrNull() as? X509Certificate
                    ?: throw IllegalStateException("TV did not provide an X.509 certificate")
                AppLogger.d(
                    "TvPairing",
                    "Received TV certificate subject=" + serverCertificate!!.subjectX500Principal.name
                )

                val requestPayload = request()
                AppLogger.d("TvPairing", "Sending pairing request bytes=" + requestPayload.size)
                send(requestPayload)

                while (true) {
                    val frame = Framing.read(s.inputStream)
                    val message = PairingMessage.parseFrom(frame)
                    AppLogger.d(
                        "TvPairing",
                        "Received pairing frame bytes=" + frame.size +
                            " status=" + message.status +
                            " type=" + pairingMessageType(message)
                    )

                    when {
                        message.status == PairingMessage.Status.STATUS_BAD_SECRET ->
                            throw IllegalStateException("TV rejected the pairing secret")

                        message.status != PairingMessage.Status.STATUS_OK ->
                            throw IllegalStateException("TV status: " + message.status)

                        message.hasPairingRequestAck() -> {
                            val payload = option()
                            AppLogger.d("TvPairing", "Sending pairing option bytes=" + payload.size)
                            send(payload)
                        }
                        message.hasPairingOption() -> {
                            val payload = config()
                            AppLogger.d("TvPairing", "Sending pairing configuration bytes=" + payload.size)
                            send(payload)
                        }
                        message.hasPairingConfigurationAck() -> {
                            AppLogger.i("TvPairing", "TV accepted pairing configuration; waiting for user code")
                            postMain(onCode)
                        }

                        message.hasPairingSecretAck() -> {
                            AppLogger.i("TvPairing", "Pairing secret accepted by TV")
                            s.close()
                            postMain { onPaired(id) }
                            return@Thread
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.e("TvPairing", "Pairing loop failed", t)
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

    private fun pairingMessageType(message: PairingMessage): String = when {
        message.hasPairingRequestAck() -> "PairingRequestAck"
        message.hasPairingOption() -> "PairingOption"
        message.hasPairingConfigurationAck() -> "PairingConfigurationAck"
        message.hasPairingSecretAck() -> "PairingSecretAck"
        else -> "Other"
    }

    suspend fun submitCode(raw: String): Boolean = withContext(Dispatchers.IO) {
        AppLogger.i("TvPairing", "submitCode called length=" + raw.length)
        try {
            val id = clientIdentity ?: return@withContext false
            val server = serverCertificate ?: return@withContext false
            val clientKey = id.cert.publicKey as? RSAPublicKey
                ?: throw IllegalStateException("Client certificate is not RSA")
            val serverKey = server.publicKey as? RSAPublicKey
                ?: throw IllegalStateException("TV certificate is not RSA")

            val code = raw.trim()
                .removePrefix("0x")
                .removePrefix("0X")
                .uppercase()

            if (code.length != 6 || code.any { it !in "0123456789ABCDEF" }) {
                AppLogger.w("TvPairing", "Pairing code rejected by local format validation")
                return@withContext false
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

            val expectedFirstByte = code.substring(0, 2).toInt(16)
            if ((secret[0].toInt() and 0xFF) != expectedFirstByte) {
                val error = IllegalArgumentException("Pairing code does not match the TLS certificates")
                AppLogger.w("TvPairing", "Pairing code failed certificate-derived verification", error)
                throw error
            }

            AppLogger.d("TvPairing", "Sending pairing secret bytes=" + secret.size)
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
            AppLogger.i("TvPairing", "Pairing secret sent successfully")
            true
        } catch (t: Throwable) {
            AppLogger.e("TvPairing", "Pairing submission failed", t)
            postMain { onError(t) }
            false
        }
    }

    fun stop() {
        AppLogger.i("TvPairing", "Stopping pairing host=" + host)
        try {
            socket?.close()
        } catch (t: Throwable) {
            AppLogger.w("TvPairing", "Pairing socket close failed", t)
        }
    }
}
