package org.fs.mael.core.config

import scala.reflect.runtime.universe._

import org.eclipse.jface.preference.IPreferenceStore

sealed trait ConfigSetting[T] {
  def id: String
  def default: T
  protected def dao: ConfigSetting.PreferenceStoreDao[T]

  private[config] def cast(v: Any): T = dao.castT(v)
  private[config] def get(s: IPreferenceStore): T = dao.getT(s, id)
  private[config] def set(s: IPreferenceStore, v: T): Unit = dao.setT(s, id, v)
  private[config] def setDefault(s: IPreferenceStore): Unit = dao.setDefaultT(s, id, default)

  override def equals(that: Any): Boolean = that match {
    case that: ConfigSetting[_] => this.id == that.id
    case _                      => false
  }
  override val hashCode: Int = this.id.hashCode
}

object ConfigSetting {

  //
  // Factory methods for external use
  //

  def apply[T](id: String, default: T): ConfigSetting[T] = {
    (default match {
      case default: Boolean => BooleanConfigSetting(id, default)
      case default: Int     => IntConfigSetting(id, default)
      case default: Long    => LongConfigSetting(id, default)
      case default: Double  => DoubleConfigSetting(id, default)
      case default: String  => StringConfigSetting(id, default)
    }).asInstanceOf[ConfigSetting[T]]
  }

  def apply(id: String, default: Option[String]): ConfigSetting[Option[String]] = {
    OptionalStringConfigSetting(id, default)
  }

  def apply[T <: RadioValue](id: String, default: T, values: Seq[T]): RadioConfigSetting[T] = {
    new RadioConfigSetting(id, default, values)
  }

  abstract class RadioValue(val id: String, val prettyName: String) {
    override def toString = s"('$prettyName', id=$id)"
  }

  //
  // Internal stuff, should not be used directly
  //

  private type GetSetting[T] = (IPreferenceStore, String) => T

  protected[config] class PreferenceStoreDao[T](
    protected[config] val castT:       (Any) => T,
    protected[config] val getT:        (IPreferenceStore, String) => T,
    protected[config] val setT:        (IPreferenceStore, String, T) => Unit,
    protected[config] val setDefaultT: (IPreferenceStore, String, T) => Unit
  )

  implicit private val booleanDao = new PreferenceStoreDao[Boolean](_.asInstanceOf[Boolean], _.getBoolean(_), _.setValue(_, _), _.setDefault(_, _))
  implicit private val intDao = new PreferenceStoreDao[Int](_.asInstanceOf[Int], _.getInt(_), _.setValue(_, _), _.setDefault(_, _))
  implicit private val longDao = new PreferenceStoreDao[Long](_.asInstanceOf[Long], _.getLong(_), _.setValue(_, _), _.setDefault(_, _))
  implicit private val doubleDao = new PreferenceStoreDao[Double](_.asInstanceOf[Double], _.getDouble(_), _.setValue(_, _), _.setDefault(_, _))
  implicit private val stringDao = new PreferenceStoreDao[String](_.asInstanceOf[String], _.getString(_), _.setValue(_, _), _.setDefault(_, _))
  implicit private val stringOptionDao = new PreferenceStoreDao[Option[String]](
    (_.asInstanceOf[String] match {
      case "" => None
      case s  => Some(s)
    }),
    (_.getString(_) match {
      case "" => None
      case s  => Some(s)
    }),
    ((s, id, v) => s.setValue(id, v getOrElse "")),
    ((s, id, v) => s.setDefault(id, v getOrElse ""))
  )

  private abstract class SimpleConfigSetting[T](implicit override val dao: PreferenceStoreDao[T]) extends ConfigSetting[T]
  private case class BooleanConfigSetting(id: String, default: Boolean) extends SimpleConfigSetting[Boolean]
  private case class IntConfigSetting(id: String, default: Int) extends SimpleConfigSetting[Int]
  private case class LongConfigSetting(id: String, default: Long) extends SimpleConfigSetting[Long]
  private case class DoubleConfigSetting(id: String, default: Double) extends SimpleConfigSetting[Double]
  private case class StringConfigSetting(id: String, default: String) extends SimpleConfigSetting[String]
  private case class OptionalStringConfigSetting(id: String, default: Option[String]) extends SimpleConfigSetting[Option[String]]

  /** Custom configuration setting, which maps to a simple value via two-way transform */
  abstract class CustomConfigSetting[T, Repr](val id: String, val default: T)(implicit reprDao: PreferenceStoreDao[Repr]) extends ConfigSetting[T] { self =>
    def toRepr(v: T): Repr
    def fromRepr(v: Repr): T
    override protected def dao: PreferenceStoreDao[T] = new PreferenceStoreDao[T](
      ((v) => fromRepr(reprDao.castT(v))),
      ((s, id) => fromRepr(reprDao.getT(s, id))),
      ((s, id, v) => reprDao.setT(s, id, toRepr(v))),
      ((s, id, v) => reprDao.setDefaultT(s, id, toRepr(v)))
    )
  }

  /** Custom configuration setting needed for choose-one radio groups */
  class RadioConfigSetting[T <: RadioValue](id: String, default: T, val values: Seq[T]) extends CustomConfigSetting[T, String](id, default) {
    def toRepr(v: T): String = v.id
    def fromRepr(v: String): T = values.find(_.id == v).get
  }
}
