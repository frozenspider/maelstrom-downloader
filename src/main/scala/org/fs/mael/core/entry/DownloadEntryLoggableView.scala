package org.fs.mael.core.entry

import java.util.UUID

trait DownloadEntryLoggableView {
  def id: UUID

  def addDownloadLogEntry(entry: LogEntry): Unit
}
