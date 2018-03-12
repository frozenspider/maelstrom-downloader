package org.fs.mael.ui.config

import org.eclipse.jface.preference.PreferenceDialog
import org.eclipse.jface.preference.PreferenceManager
import org.eclipse.swt.widgets.Shell
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.utils.CoreUtils._

class GlobalSettingsController(val globalCfgMgr: ConfigManager) {

  val mgr = new PreferenceManager().withCode { mgr =>
    def addPage(pageDescr: MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]): Unit = {
      val page = new MPreferenceNode(pageDescr, null)
      pageDescr.pathOption match {
        case None    => mgr.addToRoot(page)
        case Some(s) => mgr.addTo(s, page)
      }
    }
    GlobalSettings.pageDescriptors.foreach { pageDef =>
      addPage(pageDef)
    }
  }

  def showDialog(parent: Shell): Unit = {
    val dlg = new PreferenceDialog(parent, mgr)
    dlg.setPreferenceStore(globalCfgMgr.store)
    dlg.open()
  }
}
