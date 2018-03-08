package org.fs.mael.core.config

sealed trait ConfigOption[T] { def id: String }

object ConfigOption {
  case class SimpleConfigOption[T](id: String) extends ConfigOption[T]
  abstract class CustomConfigOption[T, Repr](val id: String) extends ConfigOption[T] {
    def asReprOption: SimpleConfigOption[Repr] = SimpleConfigOption(id)
    def toRepr(v: T): Repr
    def fromRepr(v: Repr): T
  }
  class RadioConfigOption[T <: RadioValue](id: String, val values: Seq[T]) extends CustomConfigOption[T, String](id) {
    def toRepr(v: T): String = v.id
    def fromRepr(v: String): T = values.find(_.id == v).get
  }

  abstract class RadioValue(val id: String, val prettyName: String) {
    override def toString = s"('$prettyName', id=$id)"
  }
}
