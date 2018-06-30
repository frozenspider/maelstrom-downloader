package org.fs.mael.test.stub

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.backend.AbstractBackend
import org.fs.mael.core.backend.BackendDownloader
import org.fs.mael.core.backend.ui.BackendConfigUi
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.core.entry.DownloadEntry

abstract class AbstractSimpleBackend(
  override val id:        String,
  override val globalCfg: IGlobalConfigStore = new InMemoryConfigStore with IGlobalConfigStore
) extends AbstractBackend {
  override def init(): Unit = {}

  override def downloader: BackendDownloader = new BackendDownloader(id) {
    override def eventMgr = ???
    override def transferMgr = ???
    def startInner(de: DownloadEntry, timeoutSec: Int): Unit = downloadStarted(de, timeoutSec)
    def stopInner(de: DownloadEntry): Unit = downloadStopped(de)
  }

  def downloadStarted(de: DownloadEntry, timeoutSec: Int): Unit = {}

  def downloadStopped(de: DownloadEntry): Unit = {}

  override def layoutConfig(cfgOption: Option[BackendConfigStore], tabFolder: TabFolder, isEditable: Boolean) = new BackendConfigUi {
    override def get(): BackendConfigStore = cfgOption getOrElse defaultCfg
  }

  override def pageDescriptors = Seq.empty
}
