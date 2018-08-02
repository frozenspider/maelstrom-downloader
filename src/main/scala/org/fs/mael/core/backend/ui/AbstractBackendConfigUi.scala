package org.fs.mael.core.backend.ui

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor

abstract class AbstractBackendConfigUi extends BackendConfigUi {
  def isEditable: Boolean

  def cfgOption: Option[BackendConfigStore]

  def globalCfg: IGlobalConfigStore

  def tabFolder: TabFolder

  def pageDescriptions: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]]

  /** Resulting config, should initially be empty */
  def resultCfg: BackendConfigStore

  val pages: Seq[MFieldEditorPreferencePage] = {
    cfgOption match {
      case Some(cfg) => resultCfg.resetTo(cfg) // Ignore default preferences
      case None      => resultCfg.resetTo(globalCfg)
    }
    pageDescriptions map { pageDescr =>
      createPage(pageDescr, tabFolder)
    }
  }

  override def get(): BackendConfigStore = {
    if (isEditable) {
      requireFriendly(pages.forall(_.performOk), "Some settings are invalid")
    }
    resultCfg
  }

  protected def createPage[T <: MFieldEditorPreferencePage](pageDescr: MPreferencePageDescriptor[T], tabFolder: TabFolder): T
}
