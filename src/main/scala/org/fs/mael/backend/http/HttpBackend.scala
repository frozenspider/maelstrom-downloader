package org.fs.mael.backend.http

import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.backend.http.ui.HttpConfigUi
import org.fs.mael.backend.http.ui.HttpPreferences
import org.fs.mael.core.backend.Backend
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.transfer.TransferManager

class HttpBackend(
  transferMgr:  TransferManager,
  globalCfgMgr: ConfigManager,
  eventMgr:     EventManager
) extends Backend {
  override val id: String = HttpBackend.Id

  override def isSupported(uri: URI): Boolean = {
    try {
      val url = uri.toURL
      (Seq("http", "https") contains url.getProtocol) && !url.getHost.isEmpty
    } catch {
      case ex: Exception => false
    }
  }

  override val downloader = new HttpDownloader(eventMgr, transferMgr)

  override def layoutConfig(cfgOption: Option[InMemoryConfigManager], tabFolder: TabFolder) = new HttpConfigUi(cfgOption, tabFolder, globalCfgMgr)

  override def defaultCfg: InMemoryConfigManager = {
    new InMemoryConfigManager(globalCfgMgr, HttpBackend.Id)
  }
}

object HttpBackend {
  val Id = "http"
}
