package org.fs.mael.backend.http

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.backend.http.ui.HttpConfigUi
import org.fs.mael.core.backend.Backend
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.transfer.TransferManager

class HttpBackend(eventMgr: EventManager, transferMgr: TransferManager) extends Backend {
  override type BSED = HttpEntryData

  override val dataClass: Class[BSED] = classOf[BSED]

  override val id: String = HttpBackend.Id

  override def isSupported(uri: URI): Boolean = {
    try {
      val url = uri.toURL
      (Seq("http", "https") contains url.getProtocol) && !url.getHost.isEmpty
    } catch {
      case ex: Exception => false
    }
  }

  override protected def createInner(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String
  ): DownloadEntry[HttpEntryData] = {
    DownloadEntry(id, uri, location, filenameOption, checksumOption, comment, new HttpEntryData)
  }

  override val downloader = new HttpDownloader(eventMgr, transferMgr)

  override val dataSerializer = new HttpDataSerializer

  override def layoutConfig(tabFolder: TabFolder, cfgMgr: ConfigManager) = new HttpConfigUi(tabFolder)
}

object HttpBackend {
  val Id = "http-https"
}
