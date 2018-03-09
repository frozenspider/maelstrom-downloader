package org.fs.mael.backend.http.ui

import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.TabFolder
import org.eclipse.swt.widgets.TabItem
import org.fs.mael.backend.http.HttpEntryData
import org.fs.mael.core.backend.BackendConfigUi
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.utils.CoreUtils._

class HttpConfigUi(tabFolder: TabFolder, _cfgMgr: ConfigManager) extends BackendConfigUi[HttpEntryData] {

  // TODO: Merge data with persistent config manager
  val cfgMgr = new InMemoryConfigManager

  val headersTab = new TabItem(tabFolder, SWT.NONE).withCode { tab =>
    tab.setText("Headers")
    val headersPage = new Composite(tabFolder, SWT.NONE).withCode { composite =>
      //
    }
    tab.setControl(headersPage)
  }

  def get(): HttpEntryData = {
    val result = new HttpEntryData
    result.userAgentOption = Some("A-ha-ha!")
    result
  }
}
