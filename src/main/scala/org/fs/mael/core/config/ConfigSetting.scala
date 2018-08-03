package org.fs.mael.core.config

import java.util.UUID

import org.eclipse.jface.preference.IPreferenceStore

/** Configuration setting definition. Instances should be created once per application. */
sealed trait ConfigSetting[T] {
  require(!ConfigSetting.Registry.contains(id), s"Setting '${id}' defined twice")

  // As part of init, every setting is put to a global registry
  ConfigSetting.Registry.put(id, this)

  type Repr

  def id: String
  def default: T
  protected def dao: ConfigSetting.PreferenceStoreDao[T]

  private[config] def get(s: IPreferenceStore): T = dao.getT(s, id)
  private[config] def set(s: IPreferenceStore, v: T): Unit = dao.setT(s, id, v)
  private[config] def setDefault(s: IPreferenceStore): Unit = dao.setDefaultT(s, id, default)

  private[config] def toRepr(v: T): Repr
  private[config] def fromRepr(v: Repr): T

  override def equals(that: Any): Boolean = that match {
    case that: ConfigSetting[_] => this.id == that.id
    case _                      => false
  }
  override val hashCode: Int = this.id.hashCode
  override def toString: String = "ConfigSetting(" + id + ")"
}

object ConfigSetting {

  private val Registry: scala.collection.mutable.Map[String, ConfigSetting[_]] = scala.collection.mutable.Map.empty

  def lookup(key: String): Option[ConfigSetting[_]] =
    Registry.get(key)

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
      case _                => throw new IllegalArgumentException(s"Not a primitive-like type: ${default.getClass.getSimpleName}")
    }).asInstanceOf[ConfigSetting[T]]
  }

  def apply(id: String, default: Option[String]): ConfigSetting[Option[String]] = {
    new OptionalStringConfigSetting(id, default)
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

  /** Manages store/access of some type T within {@link IPreferenceStore} */
  protected[config] class PreferenceStoreDao[T](
    protected[config] val getT:        (IPreferenceStore, String) => T,
    protected[config] val setT:        (IPreferenceStore, String, T) => Unit,
    protected[config] val setDefaultT: (IPreferenceStore, String, T) => Unit
  )

  object ImplicitDao {
    implicit val Boolean = new PreferenceStoreDao[Boolean](_.getBoolean(_), _.setValue(_, _), _.setDefault(_, _))
    implicit val Int = new PreferenceStoreDao[Int](_.getInt(_), _.setValue(_, _), _.setDefault(_, _))
    implicit val Long = new PreferenceStoreDao[Long](_.getLong(_), _.setValue(_, _), _.setDefault(_, _))
    implicit val Double = new PreferenceStoreDao[Double](_.getDouble(_), _.setValue(_, _), _.setDefault(_, _))
    implicit val String = new PreferenceStoreDao[String](_.getString(_), _.setValue(_, _), _.setDefault(_, _))
  }
  import ImplicitDao._

  private abstract class SimpleConfigSetting[T](implicit override val dao: PreferenceStoreDao[T]) extends ConfigSetting[T] {
    override type Repr = T
    override def toRepr(v: T): T = v
    override def fromRepr(v: T): T = v
  }
  private case class BooleanConfigSetting(id: String, default: Boolean) extends SimpleConfigSetting[Boolean]
  private case class IntConfigSetting(id: String, default: Int) extends SimpleConfigSetting[Int]
  private case class LongConfigSetting(id: String, default: Long) extends SimpleConfigSetting[Long]
  private case class DoubleConfigSetting(id: String, default: Double) extends SimpleConfigSetting[Double]
  private case class StringConfigSetting(id: String, default: String) extends SimpleConfigSetting[String]

  /** Custom configuration setting, which maps to a simple value via two-way transform */
  abstract class CustomConfigSetting[T, Repr2](
    val id:      String,
    val default: T
  )(implicit reprDao: PreferenceStoreDao[Repr2]) extends ConfigSetting[T] { self =>
    override type Repr = Repr2
    override def toRepr(v: T): Repr
    override def fromRepr(v: Repr): T
    override protected def dao: PreferenceStoreDao[T] = new PreferenceStoreDao[T](
      ((s, id) => fromRepr(reprDao.getT(s, id))),
      ((s, id, v) => reprDao.setT(s, id, toRepr(v))),
      ((s, id, v) => reprDao.setDefaultT(s, id, toRepr(v)))
    )
  }

  private class OptionalStringConfigSetting(
    id:      String,
    default: Option[String]
  ) extends CustomConfigSetting[Option[String], String](id, default) {
    override def toRepr(v: Option[String]): String = v getOrElse ""
    override def fromRepr(v: String): Option[String] = v match {
      case "" => None
      case s  => Some(s)
    }
  }

  /** Custom configuration setting needed for choose-one radio groups */
  class RadioConfigSetting[T <: RadioValue](
    id:         String,
    default:    T,
    val values: Seq[T]
  ) extends CustomConfigSetting[T, String](id, default) {
    override def toRepr(v: T): String = v.id
    override def fromRepr(v: String): T = values.find(_.id == v).get
  }

  /** Custom configuration setting needed for storing four-integers rectangle (x,y,w,h) */
  class RectangleConfigSetting(
    id:      String,
    default: (Int, Int, Int, Int)
  ) extends CustomConfigSetting[(Int, Int, Int, Int), String](id, default) {
    override def toRepr(v: (Int, Int, Int, Int)): String = v.toString
    override def fromRepr(v: String): (Int, Int, Int, Int) = {
      val ints = v.replaceAll("[()]", "").split(",").map(_.toInt)
      (ints(0), ints(1), ints(2), ints(3))
    }
  }

  import org.json4s._
  import org.json4s.ext.JavaTypesSerializers
  import org.json4s.jackson.Serialization

  /** Setting defining sequence of primitives, case classes or case objects. */
  class SeqConfigSetting[T: Manifest](id: String, classes: Seq[Class[_ <: T]]) extends CustomConfigSetting[Seq[T], String](id, Nil) {
    private implicit val formats = {
      Serialization.formats(ShortTypeHints(classes.toList)) ++ JavaTypesSerializers.all
    }

    override def fromRepr(s: String): Seq[T] =
      Serialization.read[Seq[T]](s)

    override def toRepr(vs: Seq[T]): String =
      Serialization.write[Seq[T]](vs)
  }

  import ConfigSettingLocalValue._

  /** Setting referencing entity from a collection stored in other setting */
  class RefConfigSetting[T <: WithPersistentId](
    id:               String,
    val defaultValue: T,
    val refSetting:   ConfigSetting[Seq[T]]
  ) extends CustomConfigSetting[UUID, String](id, defaultValue.uuid) {
    override def fromRepr(s: String): UUID = UUID.fromString(s)
    override def toRepr(v: UUID): String = v.toString
  }

  /**
   * Setting defining entity on a backend level, which takes its values from global config.
   * Should be case class or case object.
   */
  class ConfigSettingLocalEntity[T <: WithPersistentId: Manifest](
    id:                 String,
    val defaultSetting: ConfigSetting[T],
    val refSetting:     ConfigSetting[Seq[T]],
    classes:            Seq[Class[_ <: T]]
  ) extends CustomConfigSetting[ConfigSettingLocalValue[T], String](id, Default) {
    private implicit val formats = {
      Serialization.formats(ShortTypeHints(classes.toList)) ++ JavaTypesSerializers.all
    }

    private val RefR = "Ref\\(([0-9a-z-]+)\\)".r
    private val EmbeddedR = "Embedded\\((.+)\\)".r

    override def fromRepr(s: String): ConfigSettingLocalValue[T] = s match {
      case "Default"         => Default
      case RefR(uuidString)  => Ref(UUID.fromString(uuidString))
      case EmbeddedR(string) => Embedded(Serialization.read[T](string))
    }

    override def toRepr(v: ConfigSettingLocalValue[T]): String = v match {
      case Default     => "Default"
      case Ref(uuid)   => s"Ref($uuid)"
      case Embedded(v) => s"Embedded(${Serialization.write(v)})"
    }
  }
}
