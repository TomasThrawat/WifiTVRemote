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
        AppLogger.i("NSD", "scan started")
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        fun discover(type: String) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) { AppLogger.i("NSD", "discovery started type=" + serviceType) }
                override fun onDiscoveryStopped(serviceType: String) { AppLogger.i("NSD", "discovery stopped type=" + serviceType) }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { AppLogger.e("NSD", "start discovery failed type=" + serviceType + " code=" + errorCode) }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { AppLogger.e("NSD", "stop discovery failed type=" + serviceType + " code=" + errorCode) }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) { AppLogger.w("NSD", "service lost name=" + serviceInfo.serviceName) }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    AppLogger.d("NSD", "service found name=" + serviceInfo.serviceName + " type=" + serviceInfo.serviceType)
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { AppLogger.e("NSD", "resolve failed name=" + serviceInfo.serviceName + " code=" + errorCode) }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            AppLogger.i("NSD", "service resolved name=" + resolved.serviceName + " host=" + resolved.host + " port=" + resolved.port)
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
            AppLogger.i("NSD", "scan closed; stopping listeners=" + listeners.size)
            listeners.forEach {
                try {
                    nsd.stopServiceDiscovery(it)
                } catch (_: Throwable) {
                }
            }
        }
    }
}