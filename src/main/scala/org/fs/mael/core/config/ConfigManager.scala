package org.fs.mael.core.config

import java.io.ByteArrayOutputStream
import java.util.Arrays

import scala.reflect.runtime.universe._

import org.apache.commons.lang3.builder.HashCodeBuilder
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceStore
import org.eclipse.jface.util.PropertyChangeEvent

trait ConfigManager {
  import ConfigManager._

  val store: PreferenceStore

  def save(): Unit

  /**
   * Initialize default values for the given config setting.
   * Invoking this multiple times is safe and does nothing.
   */
  def initDefault(setting: ConfigSetting[_]): Unit = {
    ConfigManager.initDefault(store, setting)
  }

  def apply[T: TypeTag](setting: ConfigSetting.SimpleConfigSetting[T]): T = {
    initDefault(setting)
    // Somewhat dirty hack to overcome type erasure
    (typeOf[T] match {
      case t if t =:= typeOf[Boolean] => store.getBoolean(setting.id)
      case t if t =:= typeOf[Int]     => store.getInt(setting.id)
      case t if t =:= typeOf[Long]    => store.getLong(setting.id)
      case t if t =:= typeOf[Double]  => store.getDouble(setting.id)
      case t if t =:= typeOf[String]  => store.getString(setting.id)
    }).asInstanceOf[T]
  } ensuring (_ != null)

  def apply(setting: ConfigSetting.OptionalStringConfigSetting): Option[String] = {
    initDefault(setting)
    store.getString(setting.id) match {
      case "" => None
      case s  => Some(s)
    }
  }

  def apply[T, Repr: TypeTag](setting: ConfigSetting.CustomConfigSetting[T, Repr]): T = {
    val repr = apply[Repr](setting.asReprSetting)
    setting.fromRepr(repr)
  }

  def set[T: TypeTag](setting: ConfigSetting.SimpleConfigSetting[T], value: T): Unit = {
    // Somewhat dirty hack to overcome type erasure
    typeOf[T] match {
      case t if t =:= typeOf[Boolean] => store.setValue(setting.id, value.asInstanceOf[Boolean])
      case t if t =:= typeOf[Int]     => store.setValue(setting.id, value.asInstanceOf[Int])
      case t if t =:= typeOf[Long]    => store.setValue(setting.id, value.asInstanceOf[Long])
      case t if t =:= typeOf[Double]  => store.setValue(setting.id, value.asInstanceOf[Double])
      case t if t =:= typeOf[String]  => store.setValue(setting.id, value.asInstanceOf[String])
    }
    save()
  }

  def set(setting: ConfigSetting.OptionalStringConfigSetting, value: Option[String]): Unit = {
    store.setValue(setting.id, value getOrElse "")
  }

  def set[T, Repr: TypeTag](setting: ConfigSetting.CustomConfigSetting[T, Repr], value: T): Unit = {
    set[Repr](setting.asReprSetting, setting.toRepr(value))
  }

  def addConfigChangedListener[T](setting: ConfigSetting[T])(f: ConfigChangedEvent[T] => Unit): Unit = {
    store.addPropertyChangeListener(e => e match {
      case e if e.getProperty == setting.id =>
        setting match {
          case setting: ConfigSetting.SimpleConfigSetting[T] =>
            f(ConfigChangedEvent[T](e.getOldValue.asInstanceOf[T], e.getNewValue.asInstanceOf[T]))
          case setting: ConfigSetting.OptionalStringConfigSetting =>
            f(ConfigChangedEvent[Option[String]](e.getOldValue.asInstanceOf[Option[String]], e.getNewValue.asInstanceOf[Option[String]]))
          case setting: ConfigSetting.CustomConfigSetting[T, _] =>
            notifyCustomConfigChanged(setting, e, f)
        }
      case _ =>
      // NOOP
    })
  }

  // This is needed to encapsulate types
  private def notifyCustomConfigChanged[T, Repr](
    setting: ConfigSetting.CustomConfigSetting[T, Repr],
    e:       PropertyChangeEvent,
    f:       ConfigChangedEvent[T] => Unit
  ): Unit = {
    f(ConfigChangedEvent[T](setting.fromRepr(e.getOldValue.asInstanceOf[Repr]), setting.fromRepr(e.getNewValue.asInstanceOf[Repr])))
  }

  protected[config] def toByteArray: Array[Byte] = {
    val baos = new ByteArrayOutputStream
    store.save(baos, null)
    baos.toByteArray()
  }

  def toSerialString: String = {
    val baos = new ByteArrayOutputStream
    store.save(baos, null)
    // Charset is taken from java.util.Properties.store
    val lines = baos.toString("8859_1").split("[\r\n]+")
    // Removing comments
    lines.filter(!_.startsWith("#")).mkString("\n")
  }

  override def toString(): String = {
    val keys = store.preferenceNames().sorted
    val content = keys map (k => s"k -> ${store.getString(k)}") mkString ", "
    this.getClass.getSimpleName + "(" + content + ")"
  }

  override def equals(that: Any): Boolean = that match {
    case that: ConfigManager => Arrays.equals(this.toByteArray, that.toByteArray)
    case _                   => false
  }

  override def hashCode: Int = (new HashCodeBuilder).append(this.toByteArray).build()
}

object ConfigManager {
  case class ConfigChangedEvent[T](oldValue: T, newValue: T)

  /**
   * Initialize default values for the given config setting.
   * Invoking this multiple times is safe and does nothing.
   */
  def initDefault(store: IPreferenceStore, setting: ConfigSetting[_]): Unit = {
    setting match {
      case setting: ConfigSetting.SimpleConfigSetting[_] =>
        val oldDefault = store.getDefaultBoolean(setting.id)
        setting.default match {
          case default: Boolean => initDefault(store, setting.id, default)
          case default: Int     => initDefault(store, setting.id, default)
          case default: Long    => initDefault(store, setting.id, default)
          case default: Double  => initDefault(store, setting.id, default)
          case default: String  => initDefault(store, setting.id, default)
        }
      case setting: ConfigSetting.OptionalStringConfigSetting =>
        initDefault(store, setting.id, setting.default getOrElse "")
      case setting: ConfigSetting.CustomConfigSetting[_, _] =>
        initDefault(store, setting.asReprSetting)
    }
  }

  private def initDefault(store: IPreferenceStore, id: String, default: Boolean): Unit =
    if (store.getDefaultBoolean(id) != default) store.setDefault(id, default)

  private def initDefault(store: IPreferenceStore, id: String, default: Int): Unit =
    if (store.getDefaultInt(id) != default) store.setDefault(id, default)

  private def initDefault(store: IPreferenceStore, id: String, default: Long): Unit =
    if (store.getDefaultLong(id) != default) store.setDefault(id, default)

  private def initDefault(store: IPreferenceStore, id: String, default: Double): Unit =
    if (store.getDefaultDouble(id) != default) store.setDefault(id, default)

  private def initDefault(store: IPreferenceStore, id: String, default: String): Unit =
    if (store.getDefaultString(id) != default) store.setDefault(id, default)
}
