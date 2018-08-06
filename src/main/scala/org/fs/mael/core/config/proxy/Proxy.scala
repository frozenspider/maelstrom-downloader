package org.fs.mael.core.config.proxy

import java.util.UUID

import org.fs.mael.core.config.LocalConfigSettingValue

sealed trait Proxy extends LocalConfigSettingValue.WithPersistentId

sealed abstract class PredefinedProxy(override val uuid: UUID, override val name: String) extends Proxy
sealed abstract class CustomProxy extends Proxy

object Proxy {
  case object NoProxy extends PredefinedProxy(UUID.fromString("ef2fad04-76c2-411a-9eb7-1cf18a23c727"), "No Proxy")

  case class SOCKS5(
    uuid: UUID,
    name: String,
    host: String,
    port: Int,
    /** Use this proxy for DNS resolution as well */
    dns: Boolean,
    /** Username and password, if needed */
    authOption: Option[(String, String)]
  ) extends CustomProxy

  val Classes: Seq[Class[_ <: Proxy]] = Seq(NoProxy.getClass, classOf[SOCKS5])
}
