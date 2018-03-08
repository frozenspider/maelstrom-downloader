package org.fs.mael.ui.prefs

import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.jface.preference.PreferenceDialog
import org.eclipse.jface.preference.PreferenceManager
import org.eclipse.jface.preference.PreferenceNode
import org.eclipse.swt.widgets.Shell
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.ConfigManager
import org.fs.mael.ui.ConfigOptions

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
  class MainPage extends RichFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    def createFieldEditors(): Unit = {
      row(ConfigOptions.DownloadPath) { (option, parent) =>
        new DirectoryFieldEditor(option.id, "Download path:", parent)
      }

      row(ConfigOptions.NetworkTimeout) { (option, parent) =>
        new IntegerFieldEditor(option.id, "Network timeout (ms, 0 means no timeout):", parent).withCode { field =>
          field.setValidRange(0, 7 * 24 * 60 * 60 * 1000) // Up to one week
        }
      }

      radioRow("Action on window close:", ConfigOptions.ActionOnWindowClose)

      radioRow("Minimize to tray:", ConfigOptions.MinimizeToTrayBehaviour)
    }
  }
}
