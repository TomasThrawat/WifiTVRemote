package com.tomasthrawat.wifitvremote
import java.io.InputStream
import java.io.OutputStream
object Framing{
    fun write(o:OutputStream,b:ByteArray){require(b.size<=255);o.write(b.size);o.write(b);o.flush()}
    fun read(i:InputStream):ByteArray{val n=i.read();if(n<0)throw java.io.EOFException();val b=ByteArray(n);var p=0;while(p<n){val r=i.read(b,p,n-p);if(r<0)throw java.io.EOFException();p+=r};return b}
}
