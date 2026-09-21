package com.tomasthrawat.wifitvremote

import android.content.Context
import android.os.Build
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.util.concurrent.Executor

data class TvDevice(val name: String, val host: String, val port: Int)

class NsdDiscovery(c: Context) {
    private val nsd = c.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val executor: Executor = Runnable::run

    fun scan(): Flow<TvDevice> = callbackFlow {
        val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
        val serviceCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()

        fun emit(info: NsdServiceInfo) {
            val address = if (Build.VERSION.SDK_INT >= 34) {
                info.hostAddresses.filterIsInstance<Inet4Address>().firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                (info.host as? Inet4Address)
            }
            val host = address?.hostAddress ?: return
            trySend(TvDevice(info.serviceName, host, info.port))
        }

        fun discover(type: String) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val callback = object : NsdManager.ServiceInfoCallback {
                            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) = emit(serviceInfo)
                            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                            override fun onServiceInfoCallbackUnregistered() {}
                            override fun onServiceLost() {}
                        }
                        serviceCallbacks += callback
                        try {
                            nsd.registerServiceInfoCallback(serviceInfo, executor, callback)
                        } catch (_: IllegalArgumentException) {}
                    } else {
                        @Suppress("DEPRECATION")
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) = emit(serviceInfo)
                        })
                    }
                }
            }
            discoveryListeners += listener
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }

        discover("_androidtvremote2._tcp.")
        discover("_androidtvremote._tcp.")

        awaitClose {
            discoveryListeners.forEach {
                try { nsd.stopServiceDiscovery(it) } catch (_: Throwable) {}
            }
            if (Build.VERSION.SDK_INT >= 34) {
                serviceCallbacks.forEach {
                    try { nsd.unregisterServiceInfoCallback(it) } catch (_: Throwable) {}
                }
            }
        }
    }
}
