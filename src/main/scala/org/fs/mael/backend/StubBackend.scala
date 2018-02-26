package org.fs.mael.backend

import java.io.File
import java.net.URI

import org.fs.mael.core.backend.Backend
import org.fs.mael.core.backend.BackendDownloader
import org.fs.mael.core.entry.DownloadEntry

class StubBackend extends Backend {
  override type DE = DownloadEntry

  override val entryClass: Class[DE] = classOf[DE]

  override val id: String = "dummy"

  override def isSupported(uri: URI): Boolean = true

  override val downloader: BackendDownloader[DE] = new BackendDownloader[DE] {
    def startInner(de: DE, timeoutSec: Int): Unit = { println("started " + de) }
    def stopInner(de: DE): Unit = { println("stopped " + de) }
  }

  override protected def createInner(uri: URI, location: File): DE = {
    new DownloadEntry(uri, location, None, "Stub comment") {}
  }
}
