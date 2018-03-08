package org.fs.mael.core.config

sealed trait ConfigOption[T] {
  def id: String
  def default: T
}

object ConfigOption {
  case class SimpleConfigOption[T](id: String, default: T) extends ConfigOption[T]
  abstract class CustomConfigOption[T, Repr](val id: String, val default: T) extends ConfigOption[T] {
    def asReprOption: SimpleConfigOption[Repr] = SimpleConfigOption(id, toRepr(default))
    def toRepr(v: T): Repr
    def fromRepr(v: Repr): T
  }
  class RadioConfigOption[T <: RadioValue](id: String, default: T, val values: Seq[T]) extends CustomConfigOption[T, String](id, default) {
    def toRepr(v: T): String = v.id
    def fromRepr(v: String): T = values.find(_.id == v).get
  }

  abstract class RadioValue(val id: String, val prettyName: String) {
    override def toString = s"('$prettyName', id=$id)"
  }
}
