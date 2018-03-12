package org.fs.mael.core.backend

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor

abstract class AbstractBackendConfigUi extends BackendConfigUi {
  def backendId: String

  def isEditable: Boolean

  def cfgOption: Option[ConfigManager]

  def globalCfgMgr: ConfigManager

  def tabFolder: TabFolder

  def pageDescriptions: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]]

  val cfgMgr = new InMemoryConfigManager

  val pages: Seq[MFieldEditorPreferencePage] = {
    cfgOption match {
      case Some(cfg) => cfgMgr.resetTo(cfg, backendId) // Ignore default preferences
      case None      => cfgMgr.resetTo(globalCfgMgr, backendId)
    }
    pageDescriptions map { pageDescr =>
      createPage(pageDescr, tabFolder)
    }
  }

  override def get(): InMemoryConfigManager = {
    if (isEditable) {
      requireFriendly(pages.forall(_.performOk), "Some settings are invalid")
    }
    cfgMgr
  }

  protected def createPage[T <: MFieldEditorPreferencePage](pageDescr: MPreferencePageDescriptor[T], tabFolder: TabFolder): T
}
