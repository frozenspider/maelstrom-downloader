package org.fs.mael.test.stub

import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.list.DownloadListSerializer

/** DownloadListSerializer that does nothing, returning empty values */
class StubDownloadListSerializer extends DownloadListSerializer {
  override def serialize(entries: Iterable[DownloadEntry[_]]): String =
    ""

  override def deserialize(entriesString: String): Seq[DownloadEntry[_ <: BackendSpecificEntryData]] =
    Seq.empty
}
