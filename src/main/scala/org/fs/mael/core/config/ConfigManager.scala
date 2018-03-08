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
    import ConfigOption._
    file.getParentFile.mkdirs()
    store.setFilename(file.getAbsolutePath)
    try {
      store.load()
    } catch {
      case ex: FileNotFoundException => // NOOP
    }
  }

  /**
   * Initialize default values for the given config option.
   * Invoking this multiple times is safe and does nothing.
   */
  def initDefault(option: ConfigOption[_]): Unit = {
    ConfigManager.initDefault(store, option)
  }

  def apply[T: TypeTag](option: ConfigOption.SimpleConfigOption[T]): T = {
    initDefault(option)
    // Somewhat dirty hack to overcome type erasure
    (typeOf[T] match {
      case t if t =:= typeOf[Boolean] => store.getBoolean(option.id)
      case t if t =:= typeOf[Int]     => store.getInt(option.id)
      case t if t =:= typeOf[Long]    => store.getLong(option.id)
      case t if t =:= typeOf[Double]  => store.getDouble(option.id)
      case t if t =:= typeOf[String]  => store.getString(option.id)
    }).asInstanceOf[T]
  } ensuring (_ != null)

  def apply[T, Repr: TypeTag](option: ConfigOption.CustomConfigOption[T, Repr]): T = {
    val repr = apply[Repr](option.asReprOption)
    option.fromRepr(repr)
  }

  def set[T: TypeTag](option: ConfigOption.SimpleConfigOption[T], value: T): Unit = {
    // Somewhat dirty hack to overcome type erasure
    (typeOf[T] match {
      case t if t =:= typeOf[Boolean] => store.setValue(option.id, value.asInstanceOf[Boolean])
      case t if t =:= typeOf[Int]     => store.setValue(option.id, value.asInstanceOf[Int])
      case t if t =:= typeOf[Long]    => store.setValue(option.id, value.asInstanceOf[Long])
      case t if t =:= typeOf[Double]  => store.setValue(option.id, value.asInstanceOf[Double])
      case t if t =:= typeOf[String]  => store.setValue(option.id, value.asInstanceOf[String])
    }).asInstanceOf[T]
    store.save()
  }

  def set[T, Repr: TypeTag](option: ConfigOption.CustomConfigOption[T, Repr], value: T): Unit = {
    set[Repr](option.asReprOption, option.toRepr(value))
  }

  def addConfigChangedListener[T](option: ConfigOption[T])(f: ConfigChangedEvent[T] => Unit): Unit = {
    store.addPropertyChangeListener(e => e match {
      case e if e.getProperty == option.id =>
        option match {
          case option: ConfigOption.SimpleConfigOption[T] =>
            f(ConfigChangedEvent[T](e.getOldValue.asInstanceOf[T], e.getNewValue.asInstanceOf[T]))
          case option: ConfigOption.CustomConfigOption[T, _] =>
            notifyCustomConfigChanged(option, e, f)
        }
      case _ =>
      // NOOP
    })
  }

  // This is needed to encapsulate types
  private def notifyCustomConfigChanged[T, Repr](
    option: ConfigOption.CustomConfigOption[T, Repr],
    e:      PropertyChangeEvent,
    f:      ConfigChangedEvent[T] => Unit
  ): Unit = {
    f(ConfigChangedEvent[T](option.fromRepr(e.getOldValue.asInstanceOf[Repr]), option.fromRepr(e.getNewValue.asInstanceOf[Repr])))
  }
}

object ConfigManager {
  case class ConfigChangedEvent[T](oldValue: T, newValue: T)

  /**
   * Initialize default values for the given config option.
   * Invoking this multiple times is safe and does nothing.
   */
  def initDefault(store: IPreferenceStore, option: ConfigOption[_]): Unit = {
    option match {
      case option: ConfigOption.SimpleConfigOption[_] =>
        val oldDefault = store.getDefaultBoolean(option.id)
        option.default match {
          case default: Boolean => initDefault(store, option.id, default)
          case default: Int     => initDefault(store, option.id, default)
          case default: Long    => initDefault(store, option.id, default)
          case default: Double  => initDefault(store, option.id, default)
          case default: String  => initDefault(store, option.id, default)
        }
      case option: ConfigOption.CustomConfigOption[_, _] =>
        initDefault(store, option.asReprOption)
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
