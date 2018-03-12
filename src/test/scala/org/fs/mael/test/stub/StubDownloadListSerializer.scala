package org.fs.mael.test.stub

import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.list.DownloadListSerializer

/** DownloadListSerializer that does nothing, returning empty values */
class StubDownloadListSerializer extends DownloadListSerializer {
  override def serialize(entries: Iterable[DownloadEntry]): String =
    ""

  override def deserialize(entriesString: String): Seq[DownloadEntry] =
    Seq.empty
}
