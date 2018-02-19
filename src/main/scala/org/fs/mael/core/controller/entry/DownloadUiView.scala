package org.fs.mael.core.controller.entry

import java.util.UUID

import org.fs.mael.core.controller.LogEntry

trait DownloadUiView extends DownloadDetailsView {
  def id: UUID

  def addDownloadLogEntry(entry: LogEntry): Unit
}
