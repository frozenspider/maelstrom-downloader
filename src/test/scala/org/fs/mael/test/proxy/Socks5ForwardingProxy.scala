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
import java.net.InetSocketAddress
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Simple implementation of SOCKS5 protocol, which forwards data to specified host/port.
 *
 * @see {@link Socks5ProxyBase}
 *
 * @author FS
 */
class Socks5ForwardingProxy(
  port:                Int,
  enforceUserPassAuth: Boolean,
  targetAddr:          InetSocketAddress,
  onFailure:           Throwable => Unit
) extends Socks5ProxyBase(port, enforceUserPassAuth, onFailure) {

  override def handleReq(in: DataInputStream, out: DataOutputStream): Unit = {
    val targetSocket = new Socket()
    targetSocket.connect(targetAddr)
    val in2 = targetSocket.getInputStream
    val out2 = targetSocket.getOutputStream
    val forwardThread = Future {
      while (true) {
        out2.write(in.read())
      }
    }
    val backwardThread = Future {
      while (true) {
        out.write(in2.read())
      }
    }
    try {
      Await.ready(forwardThread, Duration.Inf)
      Await.ready(backwardThread, Duration.Inf)
    } catch {
      case ex: InterruptedException => // NOOP, expected
    }
  }
}
