package org.fs.mael.ui

import java.io.File
import java.io.FileNotFoundException

import scala.reflect.runtime.universe._

import org.eclipse.jface.preference.PreferenceStore
import org.fs.mael.core.utils.CoreUtils._

class ConfigManager(val file: File) {

  val store = new PreferenceStore().withCode { store =>
    import ConfigOptions._
    file.getParentFile.mkdirs()
    store.setFilename(file.getAbsolutePath)
    store.setDefault(DownloadPath.id, {
      sys.props("os.name") match {
        case os if os startsWith "Windows" => sys.env("USERPROFILE") + "\\Downloads"
        case _                             => sys.props("user.home") + "/downloads"
      }
    })
    store.setDefault(NetworkTimeout.id, 0)
    store.setDefault(SortColumn.id, "date-created")
    store.setDefault(SortAsc.id, true)
    store.setDefault(ActionOnWindowClose.id, OnWindowClose.Undefined.id)
    store.setDefault(MinimizeToTrayBehaviour.id, MinimizeToTray.Never.id)
    try {
      store.load()
    } catch {
      case ex: FileNotFoundException => // NOOP
    }
  }

  def getProperty[T: TypeTag](option: ConfigOptions.SimpleConfigOption[T]): T = {
    // Somewhat dirty hack to overcome type erasure
    (typeOf[T] match {
      case t if t =:= typeOf[Boolean] => store.getBoolean(option.id)
      case t if t =:= typeOf[Int]     => store.getInt(option.id)
      case t if t =:= typeOf[Long]    => store.getLong(option.id)
      case t if t =:= typeOf[Double]  => store.getDouble(option.id)
      case t if t =:= typeOf[String]  => store.getString(option.id)
    }).asInstanceOf[T]
  }

  def getProperty[T, Repr: TypeTag](option: ConfigOptions.CustomConfigOption[T, Repr]): T = {
    val repr = getProperty[Repr](option.asReprOption)
    option.fromRepr(repr)
  }

  def setProperty[T: TypeTag](option: ConfigOptions.SimpleConfigOption[T], value: T): Unit = {
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

  def setProperty[T, Repr: TypeTag](option: ConfigOptions.CustomConfigOption[T, Repr], value: T): Unit = {
    setProperty[Repr](option.asReprOption, option.toRepr(value))
  }
}
