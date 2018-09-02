package org.fs.mael.core.utils

import java.io.DataInputStream
import java.io.DataOutputStream

import org.apache.http.util.ByteArrayBuffer

trait IoUtils {

  def shortToInt(s: Short): Int = {
    if (s >= 0) s else 65536 + s
  }

  def intToShort(i: Int): Short = {
    require(i >= 0 && i < 65536)
    if (i <= 32767) i.toShort else (i - 65536).toShort
  }

  def byteToInt(b: Byte): Int = {
    if (b >= 0) b else 256 + b
  }

  def intToByte(i: Int): Byte = {
    require(i >= 0 && i < 256)
    if (i <= 127) i.toByte else (i - 256).toByte
  }

  implicit class RichDataOutputStream(out: DataOutputStream) {
    def writeAndFlush(msg: Array[Byte]): Unit = {
      out.write(msg)
      out.flush()
    }
  }

  implicit class RichDataInputStream(in: DataInputStream) {
    def readAvailable(): (Array[Byte], Int) = {
      val buf = new ByteArrayBuffer(1000)
      val tbuf = Array.ofDim[Byte](1000)
      do {
        val readLen = in.read(tbuf)
        buf.append(tbuf, 0, readLen)
      } while (in.available() > 0)
      (buf.buffer, buf.length)
    }
  }
}

object IoUtils extends IoUtils
