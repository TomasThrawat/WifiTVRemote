package com.tomasthrawat.wifitvremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address

data class TvDevice(val name: String, val host: String, val port: Int)

class NsdDiscovery(c: Context) {
    private val nsd = c.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun scan(): Flow<TvDevice> = callbackFlow {
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        fun discover(type: String) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val address = resolved.host
                            if (address is Inet4Address) {
                                trySend(
                                    TvDevice(
                                        resolved.serviceName,
                                        address.hostAddress ?: return,
                                        resolved.port
                                    )
                                )
                            }
                        }
                    })
                }
            }
            listeners += listener
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }

        discover("_androidtvremote2._tcp.")
        discover("_androidtvremote._tcp.")

        awaitClose {
            listeners.forEach {
                try {
                    nsd.stopServiceDiscovery(it)
                } catch (_: Throwable) {
                }
            }
        }
    }
}