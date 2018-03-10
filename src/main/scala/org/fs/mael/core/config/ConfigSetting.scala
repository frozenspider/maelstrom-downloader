package org.fs.mael.core.config

sealed trait ConfigSetting[T] {
  def id: String
  def default: T
}

object ConfigSetting {
  /** Simple configuration setting. Can only be of (primitive/String of the above) type */
  case class SimpleConfigSetting[T](id: String, default: T) extends ConfigSetting[T]

  /** Optional string configuration setting, treats empty string as {@code None} */
  case class OptionalStringConfigSetting(id: String, default: Option[String]) extends ConfigSetting[Option[String]]

  /** Custom configuration setting, which maps to a simple value via two-way transform */
  abstract class CustomConfigSetting[T, Repr](val id: String, val default: T) extends ConfigSetting[T] {
    def asReprSetting: SimpleConfigSetting[Repr] = SimpleConfigSetting(id, toRepr(default))
    def toRepr(v: T): Repr
    def fromRepr(v: Repr): T
  }

  /** Custom configuration setting needed for choose-one radio groups */
  class RadioConfigSetting[T <: RadioValue](id: String, default: T, val values: Seq[T]) extends CustomConfigSetting[T, String](id, default) {
    def toRepr(v: T): String = v.id
    def fromRepr(v: String): T = values.find(_.id == v).get
  }

  abstract class RadioValue(val id: String, val prettyName: String) {
    override def toString = s"('$prettyName', id=$id)"
  }
}
