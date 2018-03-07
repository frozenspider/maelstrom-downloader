package org.fs.mael.core.backend

import java.io.File

import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksums
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.transfer.TransferManager

abstract class BackendDownloader[BSED <: BackendSpecificEntryData](protected val backendId: String) {

  /** Thread group which should be used for downloading threads */
  protected lazy val dlThreadGroup = new ThreadGroup(backendId + "_download")

  /**
   * Start downloading the given entry with the given timeout
   * @param timeoutMs operations timeout in millis, 0 stands for none
   */
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

  /**
   * Should be invoked when download is complete.
   * Checks the checksum hash (if any) and advance status to either Complete or Error.
   */
  protected def checkHashAndComplete(de: DownloadEntry[BSED]): Unit = {
    val passed = de.checksumOption match {
      case Some(checksum) =>
        addLogAndFire(de, LogEntry.info("Verifying checksum"))
        Checksums.check(checksum, new File(de.location, de.filenameOption.get))
      case None =>
        true
    }
    if (passed) {
      changeStatusAndFire(de, Status.Complete)
      addLogAndFire(de, LogEntry.info("Download complete"))
    } else {
      changeStatusAndFire(de, Status.Error)
      addLogAndFire(de, LogEntry.info("Checksum doesn't match"))
    }
  }

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
