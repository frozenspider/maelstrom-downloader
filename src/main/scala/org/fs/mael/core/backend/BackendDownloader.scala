package org.fs.mael.core.backend

import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager

trait BackendDownloader[DE <: DownloadEntry] {
  def start(de: DE, timeoutSec: Int): Unit = de.status match {
    case s if s.canBeStarted => startInner(de, timeoutSec)
    case _                   => // NOOP
  }

  def startInner(de: DE, timeoutSec: Int): Unit

  def stop(de: DE): Unit = de.status match {
    case s if s.canBeStopped => stopInner(de)
    case _                   => // NOOP
  }

  def stopInner(de: DE): Unit

  //
  // Helpers
  //

  protected def addLogAndFire(de: DE, logEntry: LogEntry): Unit = {
    de.addDownloadLogEntry(logEntry)
    EventManager.fireLogged(de, logEntry)
  }

  protected def changeStatusAndFire(de: DE, newStatus: Status): Unit = {
    val prevStatus = de.status
    de.status = newStatus
    EventManager.fireStatusChanged(de, prevStatus)
  }
}
