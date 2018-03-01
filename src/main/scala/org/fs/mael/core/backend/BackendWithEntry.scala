package org.fs.mael.core.backend

import org.fs.mael.core.entry.DownloadEntry

/**
 * Dirty hack for scala type system which allows coercing DownloadEntry
 * to the path-dependent type of its corresponding backend
 *
 * @author FS
 */
class BackendWithEntry(val backend: Backend) {
  private var _de: DownloadEntry[backend.BSED] = _

  def de: DownloadEntry[backend.BSED] = _de
}

object BackendWithEntry {
  def apply(b: Backend, de: DownloadEntry[_]): BackendWithEntry = {
    require(de.backendId == b.id)
    require(b.isSupported(de.uri))
    require(b.dataClass isInstance de.backendSpecificData)
    val result = new BackendWithEntry(b)
    result._de = de.asInstanceOf[DownloadEntry[result.backend.BSED]]
    result
  }
}
