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
  respondWith:             String => String
) {

  private val serverProcess: Socks5ServerProcess = new Socks5ServerProcess
  private val serverThread: Thread = new Thread(this.serverProcess)
  private val serverSocket: ServerSocket = new ServerSocket(this.port)
  var authMethod: AuthMethod = _
  var req: String = _

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
    failures.headOption.map(throw _)
  }

  class Socks5ServerProcess extends Runnable {

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
        }
      }
    }

    private def establishConnection(socket: Socket): Unit = {
      val out = new DataOutputStream(socket.getOutputStream())
      val in = new DataInputStream(socket.getInputStream())

      // First byte is version, should be 5
      val protocolVer = in.read()
      require(protocolVer == 5, "Only SOCKS5 supported")

      authMethod = authenticate(in, out)

      // Receive CONNECT request
      val initialReq = readMessage(in)
      require(initialReq.cmd == 0x01, "Only CONNECT command is supported")

      val initialRes = Message(0x00, Addr(InetAddress.getLocalHost), port)
      val initialResBytes = initialRes.raw
      out.write(initialResBytes)
      out.flush()

      req = readPlaintext(in)
      val res = respondWith(req)
      val resBytes = res.getBytes("UTF-8")
      out.write(resBytes)
      out.flush()
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
        out.write(authMethodRes)
        out.flush()
        AuthMethod.NoAuth
      } else if (usernamePassMethodFound) {
        // See https://tools.ietf.org/html/rfc1929
        authMethodRes(1) = 0x02.toByte
        out.write(authMethodRes)
        out.flush()
        require(in.read() == 0x01, "Invalid VER during username/password negotiation")
        val uLen = in.read()
        val uBytes = Array.ofDim[Byte](uLen)
        in.readFully(uBytes)
        val pLen = in.read()
        val pBytes = Array.ofDim[Byte](pLen)
        in.readFully(pBytes)
        out.write(Array[Byte](0x01, 0x00))
        out.flush()
        AuthMethod.UserPass(new String(uBytes, "UTF-8"), new String(pBytes, "UTF-8"))
      } else {
        authMethodRes(1) = 0xFF.toByte // no acceptable methods
        out.write(authMethodRes)
        out.flush()
        throw new IOException("Authentication method not supported")
      }
    }

    private def readMessage(in: DataInputStream): Message = {
      val header = Array.ofDim[Byte](5)
      in.readFully(header, 0, 5)

      val isIpv4 = header(3) == 0x01.toByte
      val isDomain = header(3) == 0x03.toByte
      val isIpv6 = header(3) == 0x04.toByte
      if (!isIpv4 && !isDomain && !isIpv6) {
        throw new IOException("Unsupported SOCKS5 address type: " + header(3))
      }

      val addressLength = if (isDomain) header(4) else {
        // Since 1 byte is read already
        (if (isIpv4) 4 else 16) - 1
      }

      val raw = Array.ofDim[Byte](7 + addressLength)
      System.arraycopy(header, 0, raw, 0, header.length)
      in.readFully(raw, header.length, addressLength + 2)

      val dstAddr: Addr = if (isDomain) {
        Addr.Domain(new String(raw, 5, raw(4)))
      } else if (isIpv4) {
        Addr.Ipv4(InetAddress.getByAddress(null, raw.toStream.drop(4).take(4).toArray[Byte]).asInstanceOf[Inet4Address])
      } else {
        Addr.Ipv6(InetAddress.getByAddress(null, raw.toStream.drop(4).take(16).toArray[Byte]).asInstanceOf[Inet6Address])
      }
      val port = shortToInt(ByteBuffer.wrap(raw, raw.length - 2, 2).getShort)
      val res = Message(header(1), dstAddr, port)
      assert(res.raw sameElements raw)
      res
    }

    private def readPlaintext(in: DataInputStream): String = {
      val (buf, bufLen) = readBytes(in)
      new String(buf, 0, bufLen, "UTF-8")
    }

    private def readBytes(in: DataInputStream): (Array[Byte], Int) = {
      var buf = Array.ofDim[Byte](1000)
      var bufLen = 0
      val tbuf = Array.ofDim[Byte](1000)
      while (in.available() > 0) {
        val readLen = in.read(tbuf)
        if (buf.size < bufLen + readLen) {
          buf = Arrays.copyOf(buf, bufLen + readLen)
        }
        System.arraycopy(tbuf, 0, buf, bufLen, readLen)
        bufLen += readLen
      }
      (buf, bufLen)
    }
  }
}

object Socks5MockProxy {
  sealed trait Addr {
    /** Returns raw bytes representing `ATYP` followed by `BND.ADDR` - address type and its value */
    def raw: Array[Byte]
  }
  object Addr {
    case class Ipv4(value: Inet4Address) extends Addr {
      override def raw = Array[Byte](0x01) ++ value.getAddress
    }
    case class Domain(value: String) extends Addr {
      override def raw = {
        val res = Array.ofDim[Byte](value.length + 2)
        res(0) = 0x03
        val domainRaw = value.getBytes("UTF-8")
        res(1) = intToByte(domainRaw.length)
        System.arraycopy(domainRaw, 0, res, 2, domainRaw.length)
        res
      }
    }
    case class Ipv6(value: Inet6Address) extends Addr {
      override def raw = Array[Byte](0x04) ++ value.getAddress
    }
    def apply(value: InetAddress): Addr = value match {
      case value: Inet4Address => Ipv4(value)
      case value: Inet6Address => Ipv6(value)
    }
  }

  sealed trait AuthMethod
  object AuthMethod {
    case object NoAuth extends AuthMethod
    case class UserPass(username: String, password: String) extends AuthMethod
  }

  case class Message(cmd: Int, dstAddr: Addr, dstPort: Int) {
    def raw: Array[Byte] = {
      val portRaw = {
        val bb = ByteBuffer.allocate(2)
        bb.putShort(intToShort(dstPort))
        bb.array()
      }
      Array[Byte](0x05, intToByte(cmd), 0x00) ++ dstAddr.raw ++ portRaw
    }
  }

  private def shortToInt(s: Short): Int = {
    if (s >= 0) s else 65536 + s
  }

  private def intToShort(i: Int): Short = {
    require(i >= 0 && i < 65536)
    if (i <= 32767) i.toShort else (i - 65536).toShort
  }

  private def intToByte(i: Int): Byte = {
    require(i >= 0 && i < 256)
    if (i <= 127) i.toByte else (i - 256).toByte
  }

  def main(args: Array[String]): Unit = {
    val socks5Server: Socks5MockProxy = new Socks5MockProxy(7777, true, req => {
      """|HTTP/1.1 200 OK
         |Server: nginx
         |Content-Type: text/html
         |Accept-Ranges: bytes
         |
         |<html><head><title>Hello!</title></head></html>""".stripMargin
    })
    socks5Server.start()
    scala.io.StdIn.readLine()
    socks5Server.stop()
  }
}
