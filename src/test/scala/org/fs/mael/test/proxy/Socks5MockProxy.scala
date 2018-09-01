package org.fs.mael.test.proxy

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

import scala.util.Failure
import scala.util.Try

import org.fs.mael.core.proxy.Proxy.SOCKS5._
import org.fs.mael.core.utils.IoUtils._
import org.fs.mael.test.proxy.Socks5MockProxy._
import org.slf4s.Logging

/**
 * Simple mock implementation of SOCKS5 protocol.
 *
 * To test this proxy, you can use this:
 * {{{
 * curl -x socks5://localhost:proxy-port target-host-or-ip:target-port
 * curl -x socks5h://localhost:proxy-port target-host:target-port
 * curl -x socks5h://user:pass@localhost:proxy-port target-host:target-port
 * }}}
 *
 * @see {@link Socks5ProxyBase}
 *
 * @author FS
 */
class Socks5MockProxy(
  port:                Int,
  enforceUserPassAuth: Boolean,
  respondWith:         String => Array[Byte],
  onFailure:           Throwable => Unit
) extends Socks5ProxyBase(port, enforceUserPassAuth, onFailure) {
  var dataReqLog: IndexedSeq[String] = IndexedSeq.empty

  override def handleReq(in: DataInputStream, out: DataOutputStream): Unit = {
    val dataReq = readPlaintext(in)
    Socks5MockProxy.this.synchronized {
      dataReqLog = dataReqLog :+ dataReq
    }
    val dataResBytes = respondWith(dataReq)
    out.writeAndFlush(dataResBytes)
  }

  private def readPlaintext(in: DataInputStream): String = {
    val (buf, bufLen) = in.readAvailable()
    new String(buf, 0, bufLen, "UTF-8")
  }
}

object Socks5MockProxy {
  def main(args: Array[String]): Unit = {
    val socks5Server: Socks5MockProxy = new Socks5MockProxy(7777, true, req => {
      """|HTTP/1.1 200 OK
         |Server: nginx
         |Content-Type: text/html
         |Accept-Ranges: bytes
         |
         |<html><head><title>Hello!</title></head></html>"""
        .stripMargin
        .getBytes("UTF-8")
    }, th => ())
    socks5Server.start()
    scala.io.StdIn.readLine()
    socks5Server.stop()
  }
}
