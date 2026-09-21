package com.tomasthrawat.wifitvremote

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

object Framing {
    fun write(o: OutputStream, b: ByteArray) {
        AppLogger.v("Framing", "WRITE frame bytes=" + b.size)
        var n = b.size
        while (n > 0x7f) {
            o.write((n and 0x7f) or 0x80)
            n = n ushr 7
        }
        o.write(n)
        o.write(b)
        o.flush()
    }

    fun read(i: InputStream): ByteArray {
        var shift = 0
        var n = 0
        while (true) {
            val v = i.read()
            if (v < 0) {
                AppLogger.w("Framing", "READ EOF while reading frame length")
                throw EOFException()
            }
            n = n or ((v and 0x7f) shl shift)
            if ((v and 0x80) == 0) break
            shift += 7
            if (shift > 28) {
                val error = IllegalArgumentException("Invalid protobuf frame length")
                AppLogger.e("Framing", "Frame length varint exceeded supported size", error)
                throw error
            }
        }
        val b = ByteArray(n)
        var p = 0
        while (p < n) {
            val r = i.read(b, p, n - p)
            if (r < 0) {
                AppLogger.w("Framing", "READ EOF after " + p + "/" + n + " payload bytes")
                throw EOFException()
            }
            p += r
        }
        AppLogger.v("Framing", "READ frame bytes=" + b.size)
        return b
    }
}