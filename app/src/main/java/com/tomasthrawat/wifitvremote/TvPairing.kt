package com.tomasthrawat.wifitvremote
import android.content.Context
import android.os.Build
import com.google.protobuf.ByteString
import pairing.PairingMessage
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket
class TvPairing(private val context:Context,private val host:String,private val onCode:()->Unit,private val onPaired:()->Unit,private val onError:(Throwable)->Unit){
    private var socket:SSLSocket?=null
    fun start(){Thread{
        try{
            val id=CertificateStore.loadOrCreate(context,host);socket=Tls.context(id).socketFactory.createSocket() as SSLSocket
            socket!!.connect(InetSocketAddress(host,6467),8000);socket!!.useClientMode=true;socket!!.startHandshake();send(request())
            while(true){val m=PairingMessage.parseFrom(Framing.read(socket!!.inputStream));if(m.status!=PairingMessage.Status.STATUS_OK)throw IllegalStateException("TV status: "+m.status)
                when{m.hasPairingRequestAck()->send(option());m.hasPairingOption()->send(config());m.hasPairingConfigurationAck()->onCode();m.hasPairingSecretAck()->{socket!!.close();onPaired();return}}
            }
        }catch(t:Throwable){onError(t)}
    }.start()}
    private fun request()=PairingMessage.newBuilder().setProtocolVersion(2).setStatus(PairingMessage.Status.STATUS_OK).setPairingRequest(PairingMessage.PairingRequest.newBuilder().setServiceName("androidtv-remote").setClientName(Build.MODEL.ifBlank{"Wi-Fi TV Remote"})).build().toByteArray()
    private fun option()=PairingMessage.newBuilder().setProtocolVersion(2).setStatus(PairingMessage.Status.STATUS_OK).setPairingOption(PairingMessage.PairingOption.newBuilder().setPreferredRole(PairingMessage.RoleType.ROLE_TYPE_INPUT).addInputEncodings(PairingMessage.PairingEncoding.newBuilder().setType(PairingMessage.PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL).setSymbolLength(6))).build().toByteArray()
    private fun config()=PairingMessage.newBuilder().setProtocolVersion(2).setStatus(PairingMessage.Status.STATUS_OK).setPairingConfiguration(PairingMessage.PairingConfiguration.newBuilder().setClientRole(PairingMessage.RoleType.ROLE_TYPE_INPUT).setEncoding(PairingMessage.PairingEncoding.newBuilder().setType(PairingMessage.PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL).setSymbolLength(6))).build().toByteArray()
    private fun send(b:ByteArray){Framing.write(socket!!.outputStream,b)}
    fun submitCode(raw:String):Boolean{return try{
        val s=socket?:return false;val lc=s.session.localCertificates.first() as java.security.cert.X509Certificate;val sc=s.session.peerCertificates.first() as java.security.cert.X509Certificate;val a=lc.publicKey as RSAPublicKey;val b=sc.publicKey as RSAPublicKey;val code=raw.trim().removePrefix("0x").removePrefix("0X");if(code.length!=6)return false
        fun hex(n:BigInteger)=n.toString(16).padStart(512,'0');fun bytes(x:String)=ByteArray(x.length/2){i->x.substring(i*2,i*2+2).toInt(16).toByte()}
        val d=MessageDigest.getInstance("SHA-256");d.update(bytes(hex(a.modulus)));d.update(bytes("0"+hex(a.publicExponent).removePrefix("00")));d.update(bytes(hex(b.modulus)));d.update(bytes("0"+hex(b.publicExponent).removePrefix("00")));d.update(bytes(code));val secret=d.digest()
        if((secret[0].toInt() and 255)!=code.substring(0,2).toInt(16))return false
        send(PairingMessage.newBuilder().setProtocolVersion(2).setStatus(PairingMessage.Status.STATUS_OK).setPairingSecret(PairingMessage.PairingSecret.newBuilder().setSecret(ByteString.copyFrom(secret))).build().toByteArray());true
    }catch(t:Throwable){onError(t);false}}
    fun stop(){try{socket?.close()}catch(_:Throwable){}}
}
