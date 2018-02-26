package org.fs.mael.core.backend

import org.fs.mael.core.entry.DownloadEntryView

/**
 * Dirty hack for scala type system which allows coercing DownloadEntry
 * to the path-dependent type of its corresponding backend
 *
 * @author FS
 */
class BackendWithEntry(val backend: Backend) {
  private var _de: backend.DE = _

  def de: backend.DE = _de
}

object BackendWithEntry {
  def apply(b: Backend, de: DownloadEntryView): BackendWithEntry = {
    require(b.entryClass isInstance de)
    val result = new BackendWithEntry(b)
    result._de = de.asInstanceOf[result.backend.DE]
    result
  }
}
