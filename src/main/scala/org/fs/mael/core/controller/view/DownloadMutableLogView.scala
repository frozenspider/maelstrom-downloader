package org.fs.mael.core.controller.view

import java.util.UUID

import org.fs.mael.core.controller.LogEntry

trait DownloadMutableLogView {
  def id: UUID

  def addDownloadLogEntry(entry: LogEntry): Unit
}
