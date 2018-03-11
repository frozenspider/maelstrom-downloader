package org.fs.mael.backend.http.ui

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.TabFolder
import org.eclipse.swt.widgets.TabItem
import org.fs.mael.backend.http.ui.HttpPreferences._
import org.fs.mael.core.backend.BackendConfigUi
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.backend.http.HttpBackend

// TODO: Make general
class HttpConfigUi(
  cfgOption:    Option[ConfigManager],
  tabFolder:    TabFolder,
  globalCfgMgr: ConfigManager
) extends BackendConfigUi {

  val cfgMgr = new InMemoryConfigManager

  initialize()

  val headersPage: HeadersPage = {
    val tab = new TabItem(tabFolder, SWT.NONE)
    tab.setText("Headers")
    val container = new Composite(tabFolder, SWT.NONE)
    container.setLayout(new GridLayout())
    tab.setControl(container)

    val page = new HeadersPage
    page.setPreferenceStore(cfgMgr.store)
    page.noDefaultAndApplyButton()
    page.createControl(container)
    page.getControl.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))
    page
  }

  private def initialize(): Unit = {
    cfgOption match {
      case Some(cfg) => cfgMgr.resetTo(cfg, HttpBackend.Id) // Ignore default preferences
      case None      => cfgMgr.resetTo(globalCfgMgr, HttpBackend.Id)
    }
  }

  override def get(): InMemoryConfigManager = {
    requireFriendly(headersPage.performOk, "Some settings are invalid")
    cfgMgr
  }
}
