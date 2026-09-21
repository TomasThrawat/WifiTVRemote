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
    private val executor: Executor = Executor { command -> command.run() }

    init {
        AppLogger.d("NsdDiscovery", "Initialized")
    }

    fun scan(): Flow<TvDevice> = callbackFlow {
        AppLogger.i("NsdDiscovery", "scan() started")
        val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
        val serviceCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()

        fun emit(info: NsdServiceInfo) {
            val address = if (Build.VERSION.SDK_INT >= 34) {
                info.hostAddresses.filterIsInstance<Inet4Address>().firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                (info.host as? Inet4Address)
            }
            val host = address?.hostAddress
            if (host == null) {
                AppLogger.w(
                    "NsdDiscovery",
                    "Ignoring service without IPv4 address name=" + info.serviceName + " port=" + info.port
                )
                return
            }
            AppLogger.d(
                "NsdDiscovery",
                "Resolved service name=" + info.serviceName + " host=" + host + " port=" + info.port
            )
            trySend(TvDevice(info.serviceName, host, info.port))
        }

        fun discover(type: String) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    AppLogger.i("NsdDiscovery", "Discovery started type=" + serviceType)
                }
                override fun onDiscoveryStopped(serviceType: String) {
                    AppLogger.i("NsdDiscovery", "Discovery stopped type=" + serviceType)
                }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    AppLogger.e("NsdDiscovery", "Discovery start failed type=" + serviceType + " errorCode=" + errorCode)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    AppLogger.e("NsdDiscovery", "Discovery stop failed type=" + serviceType + " errorCode=" + errorCode)
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    AppLogger.w(
                        "NsdDiscovery",
                        "Service lost name=" + serviceInfo.serviceName + " port=" + serviceInfo.port
                    )
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    AppLogger.i(
                        "NsdDiscovery",
                        "Service found name=" + serviceInfo.serviceName + " port=" + serviceInfo.port
                    )
                    if (Build.VERSION.SDK_INT >= 34) {
                        val callback = object : NsdManager.ServiceInfoCallback {
                            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) = emit(serviceInfo)
                            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                                AppLogger.e("NsdDiscovery", "Service info callback registration failed errorCode=" + errorCode)
                            }
                            override fun onServiceInfoCallbackUnregistered() {
                                AppLogger.d("NsdDiscovery", "Service info callback unregistered")
                            }
                            override fun onServiceLost() {
                                AppLogger.w("NsdDiscovery", "Service info callback lost service")
                            }
                        }
                        serviceCallbacks += callback
                        try {
                            nsd.registerServiceInfoCallback(serviceInfo, executor, callback)
                            AppLogger.d("NsdDiscovery", "Registered service info callback name=" + serviceInfo.serviceName)
                        } catch (t: IllegalArgumentException) {
                            AppLogger.w("NsdDiscovery", "Service info callback registration rejected", t)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                AppLogger.e(
                                    "NsdDiscovery",
                                    "Resolve failed name=" + serviceInfo.serviceName + " errorCode=" + errorCode
                                )
                            }
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                AppLogger.d("NsdDiscovery", "Resolve succeeded name=" + serviceInfo.serviceName)
                                emit(serviceInfo)
                            }
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
            AppLogger.i("NsdDiscovery", "scan() closing")
            discoveryListeners.forEach {
                try { nsd.stopServiceDiscovery(it) } catch (t: Throwable) {
                    AppLogger.w("NsdDiscovery", "stopServiceDiscovery failed", t)
                }
            }
            if (Build.VERSION.SDK_INT >= 34) {
                serviceCallbacks.forEach {
                    try { nsd.unregisterServiceInfoCallback(it) } catch (t: Throwable) {
                        AppLogger.w("NsdDiscovery", "unregisterServiceInfoCallback failed", t)
                    }
                }
            }
        }
    }
}