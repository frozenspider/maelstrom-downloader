package org.fs.mael.backend

import java.io.File
import java.net.URI

import org.fs.mael.core.backend.Backend
import org.fs.mael.core.backend.BackendDataSerializer
import org.fs.mael.core.backend.BackendDownloader
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry

class StubBackend extends Backend {
  override type BSED = StubBackend.StubEntryData

  override val dataClass: Class[BSED] = classOf[StubBackend.StubEntryData]

  override val id: String = "dummy"

  override def isSupported(uri: URI): Boolean = true

  override val downloader: BackendDownloader[BSED] = new BackendDownloader[BSED] {
    def startInner(de: DownloadEntry[BSED], timeoutSec: Int): Unit = { println("started " + de) }
    def stopInner(de: DownloadEntry[BSED]): Unit = { println("stopped " + de) }
  }

  override val dataSerializer: BackendDataSerializer[BSED] = StubBackend.StubDataSerializer

  override protected def createInner(uri: URI, location: File): DownloadEntry[BSED] = {
    new DownloadEntry[BSED](id, uri, location, None, "Stub comment")
  }
}

object StubBackend {
  class StubEntryData extends BackendSpecificEntryData
  object StubDataSerializer extends BackendDataSerializer[StubEntryData] {
    def serialize(bsed: StubEntryData): String = ""
    def deserialize(bsedString: String): StubEntryData = new StubEntryData
  }
}
