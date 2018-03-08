package org.fs.mael.ui.prefs

import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.jface.preference.PreferenceDialog
import org.eclipse.jface.preference.PreferenceManager
import org.eclipse.jface.preference.PreferenceNode
import org.eclipse.swt.widgets.Shell
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.utils.CoreUtils._

class GlobalPreferences(val configMgr: ConfigManager) {
  import GlobalPreferences._

  val mgr = new PreferenceManager().withCode { mgr =>
    def addRootPage(id: String, label: String, clazz: Class[_]): Unit = {
      val page = new PreferenceNode("main", "Main", null, clazz.getName)
      mgr.addToRoot(page)
    }
    addRootPage("main", "Main", classOf[MainPage])
  }

  def showDialog(parent: Shell): Unit = {
    val dlg = new PreferenceDialog(parent, mgr)
    dlg.setPreferenceStore(configMgr.store)
    dlg.open()
  }
}

object GlobalPreferences {
  import org.fs.mael.core.config.ConfigOption._

  val DownloadPath: SimpleConfigOption[String] = SimpleConfigOption("main.downloadPath")
  val NetworkTimeout: SimpleConfigOption[Int] = SimpleConfigOption("main.networkTimeoutMs")
  val SortColumn: SimpleConfigOption[String] = SimpleConfigOption("view.sortColumn")
  val SortAsc: SimpleConfigOption[Boolean] = SimpleConfigOption("view.sortAsc")
  val ActionOnWindowClose: RadioConfigOption[OnWindowClose] =
    new RadioConfigOption("main.actionOnWindowClose", OnWindowClose.values)
  val MinimizeToTrayBehaviour: RadioConfigOption[MinimizeToTray] =
    new RadioConfigOption("main.minimizeToTrayBehaviour", MinimizeToTray.values)

  sealed abstract class OnWindowClose(id: String, prettyName: String) extends RadioValue(id, prettyName)
  object OnWindowClose {
    object Undefined extends OnWindowClose("", "Prompt")
    object Close extends OnWindowClose("CLOSE", "Close")
    object Minimize extends OnWindowClose("MINIMIZE", "Minimize")
    val values = Seq(Undefined, Close, Minimize)
  }

  sealed abstract class MinimizeToTray(id: String, prettyName: String) extends RadioValue(id, prettyName)
  object MinimizeToTray {
    object Never extends MinimizeToTray("NEVER", "Never")
    object OnClose extends MinimizeToTray("ON_CLOSE", "On window close")
    object Always extends MinimizeToTray("ALWAYS", "Always")
    val values = Seq(Never, OnClose, Always)
  }

  class MainPage extends RichFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    def createFieldEditors(): Unit = {
      row(DownloadPath) { (option, parent) =>
        new DirectoryFieldEditor(option.id, "Download path:", parent)
      }

      row(NetworkTimeout) { (option, parent) =>
        new IntegerFieldEditor(option.id, "Network timeout (ms, 0 means no timeout):", parent).withCode { field =>
          field.setValidRange(0, 7 * 24 * 60 * 60 * 1000) // Up to one week
        }
      }

      radioRow("Action on window close:", ActionOnWindowClose)

      radioRow("Minimize to tray:", MinimizeToTrayBehaviour)
    }
  }
}
