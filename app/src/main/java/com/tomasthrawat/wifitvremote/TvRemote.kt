package com.tomasthrawat.wifitvremote
import android.os.Build
import remote.*
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
class TvRemote(private val host:String,private val id:ClientIdentity,private val onReady:()->Unit,private val onError:(Throwable)->Unit){
    private var socket:SSLSocket?=null
    fun start(){Thread{
        try{
            socket=Tls.context(id).socketFactory.createSocket() as SSLSocket;socket!!.connect(InetSocketAddress(host,6466),8000);socket!!.useClientMode=true;socket!!.startHandshake();send(config())
            while(true){val m=RemoteMessage.parseFrom(Framing.read(socket!!.inputStream));if(m.hasRemoteConfigure()){send(config());send(RemoteMessage.newBuilder().setRemoteSetActive(RemoteSetActive.newBuilder().setActive(622)).build().toByteArray());onReady()}}
        }catch(t:Throwable){onError(t)}
    }.start()}
    private fun config()=RemoteMessage.newBuilder().setRemoteConfigure(RemoteConfigure.newBuilder().setCode1(622).setDeviceInfo(RemoteDeviceInfo.newBuilder().setModel(Build.MODEL).setVendor(Build.MANUFACTURER).setUnknown1(1).setUnknown2("1").setPackageName("androidtv-remote").setAppVersion("1.0.0"))).build().toByteArray()
    fun key(k:RemoteKeyCode){send(RemoteMessage.newBuilder().setRemoteKeyInject(RemoteKeyInject.newBuilder().setKeyCode(k).setDirection(RemoteDirection.SHORT)).build().toByteArray())}
    fun power()=key(RemoteKeyCode.KEYCODE_POWER);fun home()=key(RemoteKeyCode.KEYCODE_HOME);fun back()=key(RemoteKeyCode.KEYCODE_BACK);fun up()=key(RemoteKeyCode.KEYCODE_DPAD_UP);fun down()=key(RemoteKeyCode.KEYCODE_DPAD_DOWN);fun left()=key(RemoteKeyCode.KEYCODE_DPAD_LEFT);fun right()=key(RemoteKeyCode.KEYCODE_DPAD_RIGHT);fun ok()=key(RemoteKeyCode.KEYCODE_DPAD_CENTER);fun volumeUp()=key(RemoteKeyCode.KEYCODE_VOLUME_UP);fun volumeDown()=key(RemoteKeyCode.KEYCODE_VOLUME_DOWN);fun mute()=key(RemoteKeyCode.KEYCODE_MUTE);fun playPause()=key(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE)
    fun stop(){try{socket?.close()}catch(_:Throwable){}};private fun send(b:ByteArray){synchronized(this){Framing.write(socket!!.outputStream,b)}}
}
