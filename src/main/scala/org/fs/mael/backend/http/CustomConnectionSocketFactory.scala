package org.fs.mael.backend.http

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

import scala.io.Codec
import scala.util.Try

import org.apache.http.HttpHost
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.protocol.HttpContext
import org.fs.mael.core.connection.AbortableConnectionRegistry
import org.fs.mael.core.proxy.Proxy
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.core.utils.IoUtils._
import org.fs.utility.Imports._

/**
 * `ConnectionSocketFactory` wrapper that adds the following:
 *
 *  - Specifies lower priority traffic class hint
 *  - Supports using proxy for connection
 *  - Performs DNS resolution on its own, possibly delegating it to a proxy
 *  - Register all connections into supplied `AbortableConnectionRegistry`
 *
 * @author FS
 */
class CustomConnectionSocketFactory(
    proxy: Proxy,
    logUpdate: String => Unit,
    wrapped: ConnectionSocketFactory,
    connReg: AbortableConnectionRegistry
) extends ConnectionSocketFactory {

  override def createSocket(context: HttpContext): Socket = {
    val socket = wrapped.createSocket(context)
    socket.setTrafficClass(trafficClassFromDscp(DSCP.LowerEffort))
    socket
  }

  override def connectSocket(
    connTimeoutMs:  Int,
    socketOrNull:   Socket,
    host:           HttpHost,
    fakeRemoteAddr: InetSocketAddress,
    localAddr:      InetSocketAddress,
    context:        HttpContext
  ) = {
    assert(fakeRemoteAddr.getAddress.getAddress sameElements FakeDnsResolver.Bytes, "Use FakeDnsResolver!")
    val initialSocket = Option(socketOrNull) getOrElse createSocket(context)
    val remoteAddr = proxy match {
      case proxy: Proxy.SOCKS5 if proxy.dns => InetSocketAddress.createUnresolved(host.getHostName, fakeRemoteAddr.getPort)
      case _                                => new InetSocketAddress(host.getHostName, fakeRemoteAddr.getPort)
    }
    connReg.register(initialSocket)
    val targetSocket = proxy match {
      case Proxy.NoProxy =>
        wrapped.connectSocket(connTimeoutMs, initialSocket, host, remoteAddr, localAddr, context)
      case proxy: Proxy.SOCKS5 =>
        Socks5.connectSocket(proxy, connTimeoutMs, initialSocket, host, remoteAddr, Option(localAddr), context)
    }
    connReg.register(targetSocket)
    targetSocket
  }

  /** Append 2 Explicit Congestion Notification (ECN) bits to 6 DSCP bits*/
  private def trafficClassFromDscp(dscp: Long): Int = {
    require(dscp >= 0 && dscp < 64, "Invalid DiffServ codepoint, should be 6-bit")
    dscp.toInt << 2
  }

  /**
   * Constants for Differentiated Services Field (DS, DSCP, DiffServ) IP header
   *
   * @see [[https://tools.ietf.org/html/rfc2474 RFC 2474 (DS Field)]]
   * @see [[https://tools.ietf.org/html/rfc3662 RFC 3662 (Lower Effort)]]
   * @see [[https://support.huawei.com/enterprise/en/doc/EDOC1100027157 QoS Priority Mapping explanation by Huawei]]
   */
  private object DSCP {

    /** Aka Low-Priority Data in Service Class Markings */
    val LowerEffort: Long = 8 // 001000, CS1 class
  }

  /**
   * Encapsulates all SOCKS5 related stuff
   *
   * @see [[https://www.ietf.org/rfc/rfc1928.txt RFC 1928]]
   * @see [[https://samsclass.info/122/proj/how-socks5-works.html How Socks 5 Works]] (network traffic sniff & analysis)
   * @see [[https://stackoverflow.com/q/22937983/466646 How to use Socks 5 proxy with Apache HTTP Client 4?]] (SO)
   */
  object Socks5 {

    /** Connect to SOCKS5 proxy, authenticate if necessary, pass the remote address to it */
    def connectSocket(
      proxy:           Proxy.SOCKS5,
      connTimeoutMs:   Int,
      proxySocket:     Socket,
      host:            HttpHost,
      remoteAddr:      InetSocketAddress,
      localAddrOption: Option[InetSocketAddress],
      context:         HttpContext
    ): Socket = try {
      logUpdate(s"Connecting to SOCKS5 proxy at ${proxy.host}:${proxy.port}")
      localAddrOption map proxySocket.bind
      val proxySocketAddr = new InetSocketAddress(proxy.host, proxy.port)
      proxySocket.connect(proxySocketAddr, connTimeoutMs)
      val out = new DataOutputStream(proxySocket.getOutputStream())
      val in = new DataInputStream(proxySocket.getInputStream())

      authenticate(proxy, in, out)

      val dstAddr: Proxy.SOCKS5.Addr = remoteAddr match {
        case _ if remoteAddr.isUnresolved() => Proxy.SOCKS5.Addr.Domain(remoteAddr.getHostName)
        case _                              => Proxy.SOCKS5.Addr(remoteAddr.getAddress)
      }
      out.writeAndFlush(Proxy.SOCKS5.Message(0x01, dstAddr, remoteAddr.getPort).raw)

      val res = Proxy.SOCKS5.readMessage(in)

      if (res.cmd != 0x00) {
        val errorMsg = res.cmd match {
          case 0x01  => "General SOCKS server failure"
          case 0x02  => "Connection not allowed by ruleset"
          case 0x03  => "Network unreachable"
          case 0x04  => "Host unreachable"
          case 0x05  => "Connection refused"
          case 0x06  => "TTL expired"
          case 0x07  => "Command not supported"
          case 0x08  => "Address type not supported"
          case other => s"(unknown error code $other)"
        }
        requireFriendly(res.cmd == 0x00, "Proxy responded with an error: " + errorMsg)
      }

      logUpdate(s"Proxy connection established")
      proxySocket
    } catch {
      case ex: Exception =>
        Try(proxySocket.close())
        throw ex
    }

    private def authenticate(proxy: Proxy.SOCKS5, in: DataInputStream, out: DataOutputStream): Unit = {
      // # auth methods supported
      val authMethodsNum: Byte = if (proxy.authOption.isEmpty) 0x01 else 0x02
      out.writeAndFlush(Seq[Option[Byte]](
        Some(0x05), // Protocol ver
        Some(authMethodsNum),
        Some(0x00), // No auth
        proxy.authOption.map(auth => 0x02.toByte) orElse None // User/pass auth
      ).yieldDefined.toArray)

      val chosenAuthMethodRes = Array.ofDim[Byte](2)
      in.readFully(chosenAuthMethodRes)
      requireFriendly(chosenAuthMethodRes(0) == 0x05, "Malformed proxy response")
      val chosenAuthMethod = chosenAuthMethodRes(1)

      (chosenAuthMethod, proxy.authOption) match {
        case (0x00, _) => // No auth, no need to do anything else
        case (0x02, Some((username, password))) => // User/pass auth
          out.writeAndFlush(
            ((Seq[Byte](0x01, intToByte(username.length))
              ++ username.getBytes(Codec.UTF8.charSet)
              :+ intToByte(password.length))
              ++ password.getBytes(Codec.UTF8.charSet)).toArray
          )
          val authRes = Array.ofDim[Byte](2)
          in.readFully(authRes)
          requireFriendly(authRes(0) == 0x01, "Malformed proxy response")
          requireFriendly(authRes(1) == 0x00, "Wrong username or password for proxy")
        case _ => failFriendly("Proxy requires authentication")
      }
    }
  }
}
