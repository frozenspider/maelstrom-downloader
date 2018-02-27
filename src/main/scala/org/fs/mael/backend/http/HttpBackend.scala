package org.fs.mael.backend.http

import java.io.File
import java.net.URI

import org.fs.mael.core.backend.Backend
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry

class HttpBackend extends Backend {
  override type BSED = HttpEntryData

  override val dataClass: Class[BSED] = classOf[BSED]

  override val id: String = HttpBackend.Id

  override def isSupported(uri: URI): Boolean = {
    try {
      Seq("http", "https") contains uri.toURL.getProtocol
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

  override val downloader = new HttpBackendDownloader

  override val dataSerializer = new HttpDataSerializer
}

object HttpBackend {
  val Id = "http-https"
}
