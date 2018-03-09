package org.fs.mael.core.config

import java.io.File
import java.io.FileNotFoundException

import scala.reflect.runtime.universe._

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceStore
import org.eclipse.jface.util.PropertyChangeEvent
import org.fs.mael.core.utils.CoreUtils._

class ConfigManager(val file: File) {
  import ConfigManager._

  val store = new PreferenceStore().withCode { store =>
    import ConfigSetting._
    file.getParentFile.mkdirs()
    store.setFilename(file.getAbsolutePath)
    try {
      store.load()
    } catch {
      case ex: FileNotFoundException => // NOOP
    }
  }

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

  def apply[T, Repr: TypeTag](setting: ConfigSetting.CustomConfigSetting[T, Repr]): T = {
    val repr = apply[Repr](setting.asReprSetting)
    setting.fromRepr(repr)
  }

  def set[T: TypeTag](setting: ConfigSetting.SimpleConfigSetting[T], value: T): Unit = {
    // Somewhat dirty hack to overcome type erasure
    (typeOf[T] match {
      case t if t =:= typeOf[Boolean] => store.setValue(setting.id, value.asInstanceOf[Boolean])
      case t if t =:= typeOf[Int]     => store.setValue(setting.id, value.asInstanceOf[Int])
      case t if t =:= typeOf[Long]    => store.setValue(setting.id, value.asInstanceOf[Long])
      case t if t =:= typeOf[Double]  => store.setValue(setting.id, value.asInstanceOf[Double])
      case t if t =:= typeOf[String]  => store.setValue(setting.id, value.asInstanceOf[String])
    }).asInstanceOf[T]
    store.save()
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
