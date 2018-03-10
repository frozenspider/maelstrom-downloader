package org.fs.mael.backend.http.ui

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.TabFolder
import org.eclipse.swt.widgets.TabItem
import org.fs.mael.backend.http.HttpEntryData
import org.fs.mael.backend.http.ui.HttpPreferences._
import org.fs.mael.core.backend.BackendConfigUi
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.utils.CoreUtils._

class HttpConfigUi(
  dataOption: Option[HttpEntryData],
  tabFolder:  TabFolder,
  _cfgMgr:    ConfigManager
) extends BackendConfigUi[HttpEntryData] {

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

  // TODO: Integrate with preferences to automatically save/load from BSED?
  private def initialize(): Unit = {
    dataOption match {
      case Some(data) => initializeFromData(data)
      case None       => initializeFromDefaults()
    }
  }

  private def initializeFromData(data: HttpEntryData): Unit = {
    cfgMgr.set(UserAgent, data.userAgentOption)
  }

  private def initializeFromDefaults(): Unit = {
    // TODO: Initialize with _cfgMgr
    ???
  }

  override def get(): HttpEntryData = {
    requireFriendly(headersPage.performOk, "Some settings are invalid")
    val result = new HttpEntryData
    result.userAgentOption = cfgMgr(UserAgent)
    result
  }
}
