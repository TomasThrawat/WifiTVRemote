package com.tomasthrawat.wifitvremote

import android.content.Context
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

data class ClientIdentity(val key: PrivateKey, val cert: X509Certificate)

object CertificateStore {
    private const val PREFERENCES = "tv_identities"

    private fun bcProvider() = BouncyCastleProvider()
    private fun hostKey(host: String) = host.replace(Regex("[^0-9A-Za-z_.-]"), "_")

    fun loadOrCreate(context: Context, host: String): ClientIdentity {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val id = hostKey(host)
        val encodedKey = preferences.getString("k_" + id, null)
        val encodedCertificate = preferences.getString("c_" + id, null)

        if (encodedKey != null && encodedCertificate != null) {
            val key = KeyFactory.getInstance("RSA").generatePrivate(
                PKCS8EncodedKeySpec(Base64.decode(encodedKey, Base64.NO_WRAP))
            )
            val certificate = java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(Base64.decode(encodedCertificate, Base64.NO_WRAP).inputStream()) as X509Certificate
            return ClientIdentity(key, certificate)
        }

        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = Date()
        val end = Date(now.time + 1000L * 60 * 60 * 24 * 3650)
        val subject = X500Name("CN=WifiTVRemote,O=Android,OU=Remote,C=EG")
        val provider = bcProvider()
        val holder = JcaX509v3CertificateBuilder(
            subject, BigInteger(160, SecureRandom()), now, end, subject, keyPair.public
        ).build(JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(keyPair.private))
        val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(holder)

        preferences.edit()
            .putString("k_" + id, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
            .putString("c_" + id, Base64.encodeToString(certificate.encoded, Base64.NO_WRAP))
            .apply()

        return ClientIdentity(keyPair.private, certificate)
    }

    fun fingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    fun saveTvFingerprint(context: Context, host: String, certificate: X509Certificate) {
        saveTvFingerprint(context, host, fingerprint(certificate))
    }

    fun saveTvFingerprint(context: Context, host: String, fingerprint: String) {
        val normalized = fingerprint.filterNot { it == ':' || it.isWhitespace() }.lowercase()
        require(normalized.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256 certificate fingerprint" }
        check(
            context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString("tv_fingerprint_" + hostKey(host), normalized).commit()
        ) { "Unable to persist paired TV certificate fingerprint" }
    }

    fun loadTvFingerprint(context: Context, host: String): String? =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString("tv_fingerprint_" + hostKey(host), null)

    fun hasPairedTv(context: Context, host: String): Boolean = loadTvFingerprint(context, host) != null
}