package org.fs.mael.ui

import java.io.FileNotFoundException

import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.jface.preference.ColorFieldEditor
import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.FileFieldEditor
import org.eclipse.jface.preference.FontFieldEditor
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.jface.preference.PathEditor
import org.eclipse.jface.preference.PreferenceDialog
import org.eclipse.jface.preference.PreferenceManager
import org.eclipse.jface.preference.PreferenceNode
import org.eclipse.jface.preference.PreferenceStore
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.jface.preference.ScaleFieldEditor
import org.eclipse.jface.preference.StringFieldEditor
import org.eclipse.swt.widgets.Shell
import org.fs.mael.BuildInfo
import org.fs.mael.core.CoreUtils._
import scala.reflect.runtime.universe._

class ConfigManager {
  import ConfigManager._

  val store = new PreferenceStore().withCode { store => // TODO: Link to file
    store.setFilename(BuildInfo.name + ".prefs")
    store.setDefault(ConfigOptions.DownloadPath.id, {
      sys.props("os.name") match {
        case os if os startsWith "Windows" => sys.env("USERPROFILE") + "\\Downloads"
        case _                             => sys.props("user.home") + "/downloads"
      }
    })
    store.setDefault(ConfigOptions.NetworkTimeout.id, 0)
    try {
      store.load()
    } catch {
      case ex: FileNotFoundException => // NOOP
    }
  }

  val mgr = new PreferenceManager().withCode { mgr =>
    def addRootPage(id: String, label: String, clazz: Class[_]): Unit = {
      val page = new PreferenceNode("main", "Main", null, clazz.getName)
      mgr.addToRoot(page)
    }
    addRootPage("main", "Main", classOf[MainPage])
  }

  def showDialog(parent: Shell): Unit = {
    val dlg = new PreferenceDialog(parent, mgr)
    dlg.setPreferenceStore(store)
    dlg.open()
  }

  def getProperty[T: TypeTag](option: ConfigOptions.ConfigOption[T]): T = {
    // Somewhat dirty hack to overcome type erasure
    (typeOf[T] match {
      case t if t =:= typeOf[Boolean] => store.getBoolean(option.id)
      case t if t =:= typeOf[Int]     => store.getInt(option.id)
      case t if t =:= typeOf[Long]    => store.getLong(option.id)
      case t if t =:= typeOf[Double]  => store.getDouble(option.id)
      case t if t =:= typeOf[String]  => store.getString(option.id)
    }).asInstanceOf[T]
  }
}

object ConfigManager {
  class MainPage extends FieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    def createFieldEditors(): Unit = {
      new DirectoryFieldEditor(ConfigOptions.DownloadPath.id, "Download path:", getFieldEditorParent).withCode { field =>
        addField(field)
      }

      new IntegerFieldEditor(ConfigOptions.NetworkTimeout.id, "Network timeout (seconds, 0 means no timeout):", getFieldEditorParent()).withCode { field =>
        field.setValidRange(0, 7 * 24 * 60 * 60) // Up to one week
        addField(field)
      }
    }
  }
}
