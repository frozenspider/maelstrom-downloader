package org.fs.mael.test.proxy

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.Arrays

import scala.util.Failure
import scala.util.Try

import org.fs.mael.test.proxy.Socks5MockProxy._
import org.fs.mael.core.proxy.Proxy.SOCKS5._
import org.fs.mael.core.utils.IoUtils._

/**
 * Simple mock implementation of SOCKS5 protocol.
 * Does not support `BIND` or `UDP ASSOCIATE` commands,
 * and only supports "no auth" and "username/password" auth methods
 *
 * To test this proxy, you can use this:
 * {{{
 * curl -x socks5://localhost:proxy-port target-host-or-ip:target-port
 * curl -x socks5h://localhost:proxy-port target-host:target-port
 * curl -x socks5h://user:pass@localhost:proxy-port target-host:target-port
 * }}}
 *
 * Based on <a href=https://github.com/jitsi/smack_3_2_2/blob/master/smack/tags/smack_3_2_2/test-unit/org/jivesoftware/smackx/bytestreams/socks5/Socks5TestProxy.java>
 * smack_3_2_2 test class</a> by Henning Staib
 *
 * @see https://www.ietf.org/rfc/rfc1928.txt
 *
 * @author FS
 */
class Socks5MockProxy(
  val port:                Int,
  val enforceUserPassAuth: Boolean,
  respondWith:             String => Array[Byte],
  onFailure:               Throwable => Unit
) {

  private val serverProcess: ServerProcess = new ServerProcess
  private val serverThread: Thread = new Thread(this.serverProcess)
  private val serverSocket: ServerSocket = new ServerSocket(this.port)
  @volatile var authMethod: AuthMethod = _
  @volatile var req: String = _
  @volatile var reqCounter = 0

  def start(): Unit = this.synchronized {
    this.serverThread.start()
  }

  def stop(): Unit = this.synchronized {
    val tries = Seq(
      Try(this.serverSocket.close()),
      Try(this.serverThread.interrupt()),
      Try(this.serverThread.join())
    )
    val failures = tries.collect {
      case Failure(ex) => ex
    }
    failures.headOption.map(onFailure)
  }

  private class ServerProcess extends Runnable {

    override def run(): Unit = {
      while (!serverSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
        try {
          val socket: Socket = serverSocket.accept()
          reqCounter += 1
          try {
            establishConnection(socket)
          } finally {
            Try(socket.close())
          }
        } catch {
          case ex: SocketException => // NOOP, expected
          case th: Throwable =>
            th.printStackTrace()
            onFailure(th)
        }
      }
    }

    private def establishConnection(socket: Socket): Unit = {
      val out = new DataOutputStream(socket.getOutputStream())
      val in = new DataInputStream(socket.getInputStream())

      // First byte is version, should be 5
      val protocolVer = in.read()
      require(protocolVer == 5, s"Only SOCKS5 supported, got ${protocolVer}")

      authMethod = authenticate(in, out)

      // Receive CONNECT request
      val initialReq = readMessage(in)
      require(initialReq.cmd == 0x01, "Only CONNECT command is supported")

      val initialRes = Message(0x00, Addr(InetAddress.getLocalHost), port)
      val initialResBytes = initialRes.raw
      out.writeAndFlush(initialResBytes)

      req = readPlaintext(in)
      val resBytes = respondWith(req)
      out.writeAndFlush(resBytes)
    }

    private def authenticate(in: DataInputStream, out: DataOutputStream): AuthMethod = {
      // Second byte - number of authentication methods supported
      val authMethodsNum = in.read()

      // Read list of supported authentication methods
      val authMethods = Array.ofDim[Byte](authMethodsNum)
      in.readFully(authMethods)

      // Only authentication methods 0 and 2, no authentication and user/pass, are supported
      val noAuthMethodFound = authMethods.exists(_ == 0x00)
      val usernamePassMethodFound = authMethods.exists(_ == 0x02)

      val authMethodRes = Array.ofDim[Byte](2)
      authMethodRes(0) = 0x05.toByte // protocol version
      if (noAuthMethodFound && !enforceUserPassAuth) {
        authMethodRes(1) = 0x00.toByte
        out.writeAndFlush(authMethodRes)
        AuthMethod.NoAuth
      } else if (usernamePassMethodFound) {
        // See https://tools.ietf.org/html/rfc1929
        authMethodRes(1) = 0x02.toByte
        out.writeAndFlush(authMethodRes)
        require(in.read() == 0x01, "Invalid VER during username/password negotiation")
        val uLen = in.read()
        val uBytes = Array.ofDim[Byte](uLen)
        in.readFully(uBytes)
        val pLen = in.read()
        val pBytes = Array.ofDim[Byte](pLen)
        in.readFully(pBytes)
        out.writeAndFlush(Array[Byte](0x01, 0x00))
        AuthMethod.UserPass(new String(uBytes, "UTF-8"), new String(pBytes, "UTF-8"))
      } else {
        authMethodRes(1) = 0xFF.toByte // no acceptable methods
        out.writeAndFlush(authMethodRes)
        throw new IOException("Authentication method not supported")
      }
    }

    private def readPlaintext(in: DataInputStream): String = {
      val (buf, bufLen) = in.readBytes()
      new String(buf, 0, bufLen, "UTF-8")
    }
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
