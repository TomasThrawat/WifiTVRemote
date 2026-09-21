package com.tomasthrawat.wifitvremote

import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

object Tls {
    fun context(id: ClientIdentity): SSLContext {
        AppLogger.d("Tls", "Creating TLS context with client identity")
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        val pass = "wifitv".toCharArray()
        ks.setKeyEntry("client", id.key, pass, arrayOf(id.cert))

        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        km.init(ks, pass)

        val trust = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        return SSLContext.getInstance("TLS").apply {
            init(km.keyManagers, arrayOf<TrustManager>(trust), SecureRandom())
            AppLogger.d("Tls", "TLS context initialized")
        }
    }
}