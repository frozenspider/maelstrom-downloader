package org.fs.mael.ui.prefs

import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.jface.preference.PreferenceDialog
import org.eclipse.jface.preference.PreferenceManager
import org.eclipse.swt.widgets.Shell
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.ConfigSetting
import org.fs.mael.core.utils.CoreUtils._

class GlobalPreferences(val globalCfgMgr: ConfigManager) {
  import GlobalPreferences._

  val mgr = new PreferenceManager().withCode { mgr =>
    def addRootPage(id: String, label: String, clazz: Class[_ <: MFieldEditorPreferencePage]): Unit = {
      val page = new MPreferenceNode("main", "Main", null, clazz)
      mgr.addToRoot(page)
    }
    addRootPage("main", "Main", classOf[MainPage])
  }

  def showDialog(parent: Shell): Unit = {
    val dlg = new PreferenceDialog(parent, mgr)
    dlg.setPreferenceStore(globalCfgMgr.store)
    dlg.open()
  }
}

object GlobalPreferences {
  import org.fs.mael.core.config.ConfigSetting.RadioConfigSetting
  import org.fs.mael.core.config.ConfigSetting.RadioValue

  val DownloadPath: ConfigSetting[String] =
    ConfigSetting("main.downloadPath", {
      sys.props("os.name") match {
        case os if os startsWith "Windows" => sys.env("USERPROFILE") + "\\Downloads"
        case _                             => sys.props("user.home") + "/downloads"
      }
    })

  val NetworkTimeout: ConfigSetting[Int] =
    ConfigSetting("main.networkTimeoutMs", 0)

  val OnWindowCloseBehavior: RadioConfigSetting[OnWindowClose] =
    ConfigSetting("main.onWindowClose", OnWindowClose.Undefined, OnWindowClose.values)

  val MinimizeToTrayBehavior: RadioConfigSetting[MinimizeToTray] =
    ConfigSetting("main.minimizeToTray", MinimizeToTray.Never, MinimizeToTray.values)

  val ShowTrayIconBehavior: RadioConfigSetting[ShowTrayIcon] =
    ConfigSetting("main.showTrayIcon", ShowTrayIcon.Always, ShowTrayIcon.values)

  val SortColumn: ConfigSetting[String] =
    ConfigSetting("view.sortColumn", "date-created")

  val SortAsc: ConfigSetting[Boolean] =
    ConfigSetting("view.sortAsc", true)

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

  sealed abstract class ShowTrayIcon(id: String, prettyName: String) extends RadioValue(id, prettyName)
  object ShowTrayIcon {
    object Always extends ShowTrayIcon("ALWAYS", "Always")
    object WhenNeeded extends ShowTrayIcon("WHEN_NEEDED", "Only when minimized to tray")
    val values = Seq(Always, WhenNeeded)
  }

  class MainPage extends MFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      row(DownloadPath) { (setting, parent) =>
        new DirectoryFieldEditor(setting.id, "Download path:", parent)
      }

      row(NetworkTimeout) { (setting, parent) =>
        new IntegerFieldEditor(setting.id, "Network timeout (ms, 0 means no timeout):", parent).withCode { field =>
          field.setValidRange(0, 7 * 24 * 60 * 60 * 1000) // Up to one week
        }
      }

      radioRow("Action on window close:", OnWindowCloseBehavior)

      radioRow("Minimize to tray:", MinimizeToTrayBehavior)

      radioRow("Show tray icon:", ShowTrayIconBehavior)
    }
  }
}
