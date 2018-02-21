package org.fs.mael.backend.http

import java.net.URI

import org.fs.mael.core.Backend
import org.fs.mael.core.entry.DownloadEntry
import java.io.File

class HttpBackend extends Backend {
  override type DE = HttpBackend.DE

  override val entryClass: Class[DE] = classOf[DE]

  override val id: String = HttpBackend.Id

  override def isSupported(uri: URI): Boolean = {
    try {
      Seq("http", "https") contains uri.toURL.getProtocol
    } catch {
      case ex: Exception => false
    }
  }

  override protected def createInner(uri: URI, location: File): DE = {
    new DownloadEntry(uri, location, None, "HTTP!11") {}
  }

  override val downloader = new HttpBackendDownloader
}

object HttpBackend {
  type DE = DownloadEntry

  val Id = "http-https"
}
