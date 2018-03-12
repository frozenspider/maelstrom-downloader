package org.fs.mael.core.list

import org.fs.mael.core.entry.DownloadEntry

trait DownloadListSerializer {
  def serialize(entries: Iterable[DownloadEntry]): String

  def deserialize(entriesString: String): Seq[DownloadEntry]
}
