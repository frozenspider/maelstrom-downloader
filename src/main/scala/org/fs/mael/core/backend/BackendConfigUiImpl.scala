package org.fs.mael.core.backend

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.TabFolder
import org.eclipse.swt.widgets.TabItem
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor
import org.fs.mael.ui.utils.SwtUtils

class BackendConfigUiImpl(
  backendId:  String,
  pageDescr:  Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]],
  isEditable: Boolean
) extends BackendConfigUi {

  val cfgMgr = new InMemoryConfigManager

  var pages: Seq[MFieldEditorPreferencePage] = Seq.empty

  def initialize(
    cfgOption:    Option[ConfigManager],
    tabFolder:    TabFolder,
    globalCfgMgr: ConfigManager
  ): Unit = {
    cfgOption match {
      case Some(cfg) => cfgMgr.resetTo(cfg, backendId) // Ignore default preferences
      case None      => cfgMgr.resetTo(globalCfgMgr, backendId)
    }
    pageDescr.foreach { pageDef =>
      createPage(pageDef, tabFolder)
    }
  }

  override def get(): InMemoryConfigManager = {
    require(!pages.isEmpty, "Forgot to call initialize()?")
    if (isEditable) {
      requireFriendly(pages.forall(_.performOk), "Some settings are invalid")
    }
    cfgMgr
  }

  private def createPage(pageDescr: MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage], tabFolder: TabFolder): Unit = {
    val tab = new TabItem(tabFolder, SWT.NONE)
    tab.setText(pageDescr.name)
    val container = new Composite(tabFolder, SWT.NONE)
    container.setLayout(new GridLayout())
    tab.setControl(container)

    val page = pageDescr.clazz.newInstance()
    page.setPreferenceStore(cfgMgr.store)
    page.noDefaultAndApplyButton()
    page.createControl(container)
    page.getControl.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))
    if (!isEditable) {
      page.fieldEditorsWithParents.foreach {
        case (editor, parent) => SwtUtils.disable(editor, parent)
      }
    }

    pages = pages :+ page
  }
}
