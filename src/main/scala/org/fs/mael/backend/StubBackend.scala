package org.fs.mael.backend

import org.fs.mael.core.Backend
import org.fs.mael.core.entry.DownloadEntry
import java.net.URI

class StubBackend extends Backend {
  override type DE = DownloadEntry

  override val entryClass: Class[DE] = classOf[DE]

  override val id: String = "dummy"

  override def isSupported(uri: URI): Boolean = true

  override protected def createInner(uri: URI): DE = {
    new DownloadEntry(uri, None, "Stub comment") {}
  }
}
