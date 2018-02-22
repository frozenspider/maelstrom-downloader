package org.fs.mael.core

import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager

trait BackendDownloader[DE <: DownloadEntry] {
  import Status._

  def start(de: DE): Unit = de.status match {
    case s if s.canBeStarted => startInner(de)
    case _                   => // NOOP
  }

  def startInner(de: DE): Unit

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
