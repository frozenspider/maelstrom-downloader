package org.fs.mael.ui.config

import scala.collection.JavaConverters

import org.eclipse.jface.preference.PreferenceDialog
import org.eclipse.jface.preference.PreferenceManager
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.http.config.HttpSettings
import org.fs.mael.core.config.ConfigStore
import org.fs.mael.core.utils.CoreUtils._

class GlobalSettingsController(val globalCfg: ConfigStore) {

  val mgr = new PreferenceManager().withCode { mgr =>
    def addPage(pageDescr: MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]): Unit = {
      val page = new MPreferenceNode(pageDescr, null)
      pageDescr.pathOption match {
        case None    => mgr.addToRoot(page)
        case Some(s) => mgr.addTo(s, page)
      }
    }
    // TODO: Initialize elsewhere?
    val pageDescriptors = (GlobalSettings.pageDescriptors
      ++ HttpSettings.Global.pageDescriptors)
    pageDescriptors.foreach { pageDef =>
      addPage(pageDef)
    }
  }

  def showDialog(parent: Shell): Unit = {
    initPages()
    val dlg = new PreferenceDialog(parent, mgr)
    dlg.setPreferenceStore(globalCfg.inner)
    dlg.open()
  }

  /** Initialize pages, setting config store for them*/
  private def initPages(): Unit = {
    val prefNodes = JavaConverters.asScalaBuffer(mgr.getElements(PreferenceManager.PRE_ORDER))
    prefNodes foreach { pn =>
      if (pn.getPage == null) pn.createPage()
      pn.getPage match {
        case page: MFieldEditorPreferencePage => page.setConfigStore(globalCfg)
        case _                                => // NOOP
      }
    }
  }
}
