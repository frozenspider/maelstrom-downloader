package org.fs.mael.core.backend

import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.transfer.TransferManager

abstract class BackendDownloader[BSED <: BackendSpecificEntryData](protected val backendId: String) {

  /** Thread group which should be used for downloading threads */
  protected lazy val dlThreadGroup = new ThreadGroup(backendId + "_download")

  def start(de: DownloadEntry[BSED], timeoutMs: Int): Unit = de.status match {
    case s if s.canBeStarted => startInner(de, timeoutMs)
    case _                   => // NOOP
  }

  def startInner(de: DownloadEntry[BSED], timeoutMs: Int): Unit

  def stop(de: DownloadEntry[BSED]): Unit = de.status match {
    case s if s.canBeStopped => stopInner(de)
    case _                   => // NOOP
  }

  def stopInner(de: DownloadEntry[BSED]): Unit

  //
  // Helpers
  //

  protected def eventMgr: EventManager

  protected def transferMgr: TransferManager

  protected def addLogAndFire(de: DownloadEntry[BSED], logEntry: LogEntry): Unit = {
    de.addDownloadLogEntry(logEntry)
    eventMgr.fireLogged(de, logEntry)
  }

  protected def changeStatusAndFire(de: DownloadEntry[BSED], newStatus: Status): Unit = {
    val prevStatus = de.status
    de.status = newStatus
    eventMgr.fireStatusChanged(de, prevStatus)
  }
}
