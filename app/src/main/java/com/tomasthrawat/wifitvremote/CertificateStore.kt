package com.tomasthrawat.wifitvremote

import android.content.Context
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

data class ClientIdentity(val key: PrivateKey, val cert: X509Certificate)

object CertificateStore {
    private fun bcProvider(): BouncyCastleProvider = BouncyCastleProvider()

    fun loadOrCreate(c: Context, host: String): ClientIdentity {
        
        val p = c.getSharedPreferences("tv_identities", Context.MODE_PRIVATE)
        val id = host.replace(Regex("[^0-9A-Za-z_.-]"), "_")
        val kb = p.getString("k_$id", null)
        val cb = p.getString("c_$id", null)

        if (kb != null && cb != null) {
            val k = KeyFactory.getInstance("RSA").generatePrivate(
                PKCS8EncodedKeySpec(Base64.decode(kb, Base64.NO_WRAP))
            )
            val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(
                    Base64.decode(cb, Base64.NO_WRAP).inputStream()
                ) as X509Certificate
            
            return ClientIdentity(k, cert)
        }

        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = java.util.Date()
        val end = java.util.Date(now.time + 1000L * 60 * 60 * 24 * 3650)
        val subject = X500Name("CN=WifiTVRemote,O=Android,OU=Remote,C=EG")
        val provider = bcProvider()

        val holder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()),
            now,
            end,
            subject,
            kp.public
        ).build(
            JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(provider)
                .build(kp.private)
        )

        val cert = JcaX509CertificateConverter()
            .setProvider(provider)
            .getCertificate(holder)

        p.edit()
            .putString("k_$id", Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
            .putString("c_$id", Base64.encodeToString(cert.encoded, Base64.NO_WRAP))
            .apply()

        
        return ClientIdentity(kp.private, cert)
    }
}
