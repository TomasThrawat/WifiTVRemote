package com.tomasthrawat.wifitvremote
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
data class TvDevice(val name:String,val host:String,val port:Int)
class NsdDiscovery(c:Context){
    private val nsd=c.getSystemService(Context.NSD_SERVICE) as NsdManager
    fun scan():Flow<TvDevice>=callbackFlow{
        val ls=mutableListOf<NsdManager.DiscoveryListener>()
        fun d(type:String){val l=object:NsdManager.DiscoveryListener{
            override fun onDiscoveryStarted(t:String)=Unit;override fun onDiscoveryStopped(t:String)=Unit;override fun onStartDiscoveryFailed(t:String,e:Int)=Unit;override fun onStopDiscoveryFailed(t:String,e:Int)=Unit;override fun onServiceLost(s:NsdServiceInfo)=Unit
            override fun onServiceFound(s:NsdServiceInfo){nsd.resolveService(s,object:NsdManager.ResolveListener{override fun onResolveFailed(s:NsdServiceInfo,e:Int)=Unit;override fun onServiceResolved(s:NsdServiceInfo){val a=s.host;if(a is Inet4Address)trySend(TvDevice(s.serviceName,a.hostAddress ?: return,s.port))}})}
        };ls+=l;nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,l)}
        d("_androidtvremote2._tcp.");d("_androidtvremote._tcp.");awaitClose{ls.forEach{try{nsd.stopServiceDiscovery(it)}catch(_:Throwable){}}}
    }
}
