package org.fs.mael.backend.http

import java.io.File
import java.net.URI

import org.fs.mael.core.backend.Backend
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventManager

class HttpBackend(eventMgr: EventManager) extends Backend {
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
    comment:        String
  ): DownloadEntry[HttpEntryData] = {
    DownloadEntry(id, uri, location, filenameOption, comment, new HttpEntryData)
  }

  override val downloader = new HttpBackendDownloader(eventMgr)

  override val dataSerializer = new HttpDataSerializer
}

object HttpBackend {
  val Id = "http-https"
}
