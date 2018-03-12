package org.fs.mael.test.stub

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.backend.AbstractBackend
import org.fs.mael.core.backend.BackendConfigUi
import org.fs.mael.core.backend.BackendDownloader
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.config.ConfigManager

abstract class AbstractSimpleBackend(
  override val id:           String,
  override val globalCfgMgr: ConfigManager = new InMemoryConfigManager
) extends AbstractBackend {
  override def downloader: BackendDownloader = new BackendDownloader(id) {
    override def eventMgr = ???
    override def transferMgr = ???
    def startInner(de: DownloadEntry, timeoutSec: Int): Unit = downloadStarted(de, timeoutSec)
    def stopInner(de: DownloadEntry): Unit = downloadStopped(de)
  }

  def downloadStarted(de: DownloadEntry, timeoutSec: Int): Unit = {}

  def downloadStopped(de: DownloadEntry): Unit = {}

  override def layoutConfig(cfgOption: Option[InMemoryConfigManager], tabFolder: TabFolder, isEditable: Boolean) = new BackendConfigUi {
    override def get(): InMemoryConfigManager = cfgOption getOrElse defaultCfg
  }

  override def pageDescriptors = Seq.empty
}
