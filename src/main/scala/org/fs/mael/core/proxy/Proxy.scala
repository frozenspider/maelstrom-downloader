package org.fs.mael.core.proxy

import java.io.DataInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.UUID

import org.fs.mael.core.config.LocalConfigSettingValue
import org.fs.mael.core.utils.IoUtils._

sealed trait Proxy extends LocalConfigSettingValue.WithPersistentId

object Proxy {
  sealed abstract class PredefinedProxy(override val uuid: UUID, override val name: String) extends Proxy
  sealed abstract class CustomProxy extends Proxy

  case object NoProxy extends PredefinedProxy(UUID.fromString("ef2fad04-76c2-411a-9eb7-1cf18a23c727"), "<No Proxy>")

  case class SOCKS5(
    uuid: UUID,
    name: String,
    host: String,
    port: Int,
    /** Username and password, if needed */
    authOption: Option[(String, String)],
    /** Use this proxy for DNS resolution as well */
    dns: Boolean
  ) extends CustomProxy

  object SOCKS5 {
    sealed trait Addr {
      /** Returns raw bytes representing `ATYP` followed by `BND.ADDR` - address type and its value */
      def raw: Array[Byte]
      def resolve: InetAddress
    }
    object Addr {
      case class Ipv4(value: Inet4Address) extends Addr {
        override def raw = Array[Byte](0x01) ++ value.getAddress
        override def resolve = value
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
        override def resolve = InetAddress.getByName(value)
      }
      case class Ipv6(value: Inet6Address) extends Addr {
        override def raw = Array[Byte](0x04) ++ value.getAddress
        override def resolve = value
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

    def readMessage(in: DataInputStream): Message = {
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
  }

  val Classes: Seq[Class[_ <: Proxy]] = Seq(NoProxy.getClass, classOf[SOCKS5])
}
