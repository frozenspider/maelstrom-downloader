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
 * Base class for simple implementations of SOCKS5 protocol.
 * Does not support `BIND` or `UDP ASSOCIATE` commands,
 * and only supports "no auth" and "username/password" auth methods
 *
 * Based on <a href=https://github.com/jitsi/smack_3_2_2/blob/master/smack/tags/smack_3_2_2/test-unit/org/jivesoftware/smackx/bytestreams/socks5/Socks5TestProxy.java>
 * smack_3_2_2 test class</a> by Henning Staib
 *
 * @see https://www.ietf.org/rfc/rfc1928.txt
 *
 * @author FS
 */
abstract class Socks5ProxyBase(
  val port:                Int,
  val enforceUserPassAuth: Boolean,
  onFailure:               Throwable => Unit
) extends Logging {

  private val serverThread: Thread = new ServerProcess()
  private val serverSocket: ServerSocket = new ServerSocket(this.port)
  /** `Seq[AuthMethod, ConnectMessage]` */
  var connLog: IndexedSeq[(AuthMethod, Message)] = IndexedSeq.empty

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

  protected def handleReq(in: DataInputStream, out: DataOutputStream): Unit

  private class ServerProcess extends Thread {

    setUncaughtExceptionHandler((thread, th) => {
      log.error("Uncaught exception in SOCKS5 mock proxy", th)
    })

    override def run(): Unit = {
      while (!serverSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
        try {
          val socket: Socket = serverSocket.accept()
          try {
            establishConnection(socket)
          } finally {
            Try(socket.close())
          }
        } catch {
          case ex: SocketException => // NOOP, expected
          case th: Throwable =>
            log.error("Error processing socket connection", th)
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

      val authMethod = authenticate(in, out)

      // Receive CONNECT request
      val connReq = readMessage(in)
      require(connReq.cmd == 0x01, "Only CONNECT command is supported")
      Socks5ProxyBase.this.synchronized {
        connLog = connLog :+ (authMethod, connReq)
      }

      // We're responding exactly like Tor does - with zeroed address/port
      val connRes = Message(0x00, Addr(InetAddress.getByAddress(Array[Byte](0, 0, 0, 0))), 0)
      out.writeAndFlush(connRes.raw)

      handleReq(in, out)
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
  }
}
