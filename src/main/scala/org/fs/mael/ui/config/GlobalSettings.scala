package org.fs.mael.ui.config

import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.fs.mael.core.config.ConfigSetting
import org.fs.mael.core.config.ConfigSetting._
import org.fs.mael.core.config.proxy.Proxy
import org.fs.mael.core.utils.CoreUtils._

object GlobalSettings {

  val pageDescriptors: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]] = Seq(
    MPreferencePageDescriptor("Main", None, classOf[MainPage])
  )

  //
  // Settings
  //

  val DownloadPath: ConfigSetting[String] =
    ConfigSetting("main.downloadPath", {
      sys.props("os.name") match {
        case os if os startsWith "Windows" => sys.env("USERPROFILE") + "\\Downloads"
        case _                             => sys.props("user.home") + "/downloads"
      }
    })

  val ConnectionTimeout: ConfigSetting[Int] =
    ConfigSetting("main.connection.timeoutMs", 0)

  val ConnectionProxies: ConfigSetting[Seq[Proxy]] =
    new SeqConfigSetting[Proxy]("main.connection.proxies", Proxy.Classes)

  val ConnectionProxy: RefConfigSetting[Proxy] =
    new RefConfigSetting("main.connection.proxy", Proxy.NoProxy, ConnectionProxies)

  val OnWindowCloseBehavior: RadioConfigSetting[OnWindowClose] =
    ConfigSetting("main.onWindowClose", OnWindowClose.Undefined, OnWindowClose.values)

  val MinimizeToTrayBehavior: RadioConfigSetting[MinimizeToTray] =
    ConfigSetting("main.minimizeToTray", MinimizeToTray.Never, MinimizeToTray.values)

  val ShowTrayIconBehavior: RadioConfigSetting[ShowTrayIcon] =
    ConfigSetting("main.showTrayIcon", ShowTrayIcon.Always, ShowTrayIcon.values)

  val AutoStartDownloads: ConfigSetting[Boolean] =
    ConfigSetting("main.autoStartDownloads", true)

  val SortColumn: ConfigSetting[String] =
    ConfigSetting("view.sortColumn", "date-created")

  val SortAsc: ConfigSetting[Boolean] =
    ConfigSetting("view.sortAsc", true)

  val WindowIsMaximized: ConfigSetting[Boolean] =
    ConfigSetting("view.window.isMaximized", false)

  val WindowBounds: RectangleConfigSetting =
    new RectangleConfigSetting("view.window.bounds", (0, 0, 0, 0))

  // TODO: File size/speed granularity display

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

  //
  // Pages
  //

  private class MainPage extends MFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      row(DownloadPath) { (setting, parent) =>
        new DirectoryFieldEditor(setting.id, "Download path:", parent)
      }

      row(ConnectionTimeout) { (setting, parent) =>
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
