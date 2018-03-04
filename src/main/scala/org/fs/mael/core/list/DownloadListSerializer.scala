package org.fs.mael.core.list

import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry

trait DownloadListSerializer {
  def serialize(entries: Iterable[DownloadEntry[_]]): String

  def deserialize(entriesString: String): Seq[DownloadEntry[_ <: BackendSpecificEntryData]]
}
