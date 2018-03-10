package org.fs.mael.test.stub

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.backend.Backend
import org.fs.mael.core.backend.BackendConfigUi
import org.fs.mael.core.backend.BackendDownloader
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.entry.DownloadEntry

abstract class AbstractSimpleBackend(
  override val id: String
) extends Backend {
  override def downloader: BackendDownloader = new BackendDownloader(id) {
    override def eventMgr = ???
    override def transferMgr = ???
    def startInner(de: DownloadEntry, timeoutSec: Int): Unit = downloadStarted(de, timeoutSec)
    def stopInner(de: DownloadEntry): Unit = downloadStopped(de)
  }

  def downloadStarted(de: DownloadEntry, timeoutSec: Int): Unit = {}

  def downloadStopped(de: DownloadEntry): Unit = {}

  override def defaultCfg = new InMemoryConfigManager

  override def layoutConfig(cfgOption: Option[InMemoryConfigManager], tabFolder: TabFolder) = new BackendConfigUi {
    override def get(): InMemoryConfigManager = cfgOption getOrElse defaultCfg
  }
}
