package org.fs.mael.core.config

import java.io.File
import java.io.FileNotFoundException

import scala.reflect.runtime.universe._

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceStore
import org.fs.mael.core.utils.CoreUtils._

class ConfigManager(val file: File) {

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

  /** Initialize default values for the given config option. Invoking this multiple times is safe. */
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
}

object ConfigManager {
  def initDefault(store: IPreferenceStore, option: ConfigOption[_]): Unit = {
    option match {
      case option: ConfigOption.SimpleConfigOption[_] =>
        option.default match {
          case default: Boolean => store.setDefault(option.id, default)
          case default: Int     => store.setDefault(option.id, default)
          case default: Long    => store.setDefault(option.id, default)
          case default: Double  => store.setDefault(option.id, default)
          case default: String  => store.setDefault(option.id, default)
        }
      case option: ConfigOption.CustomConfigOption[_, _] =>
        initDefault(store, option.asReprOption)
    }
  }
}
