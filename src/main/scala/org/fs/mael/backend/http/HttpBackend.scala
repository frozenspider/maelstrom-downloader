package org.fs.mael.backend.http

import java.net.URI

import org.fs.mael.backend.http.config.HttpSettings
import org.fs.mael.core.backend.AbstractBackend
import org.fs.mael.core.config.ConfigStore
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.transfer.TransferManager

class HttpBackend(
  transferMgr:            TransferManager,
  override val globalCfg: ConfigStore,
  eventMgr:               EventManager
) extends AbstractBackend {
  override val id: String = HttpBackend.Id

  override def init(): Unit = {
    HttpSettings
  }

  override def isSupported(uri: URI): Boolean = {
    try {
      val url = uri.toURL
      (Seq("http", "https") contains url.getProtocol) && !url.getHost.isEmpty
    } catch {
      case ex: Exception => false
    }
  }

  override val downloader = new HttpDownloader(eventMgr, transferMgr)

  override def pageDescriptors = HttpSettings.Local.pageDescriptors
}

object HttpBackend {
  val Id = "http"
}
