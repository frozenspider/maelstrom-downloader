package org.fs.mael.core.entry

import java.util.UUID

trait DownloadUiView extends DownloadDetailsView {
  def id: UUID

  def addDownloadLogEntry(entry: LogEntry): Unit
}
