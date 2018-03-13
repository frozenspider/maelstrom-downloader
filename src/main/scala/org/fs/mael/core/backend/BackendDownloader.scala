package org.fs.mael.core.backend

import java.io.File

import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksums
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.transfer.TransferManager

abstract class BackendDownloader(protected val backendId: String) {

  /** Thread group which should be used for downloading threads */
  protected lazy val dlThreadGroup = new ThreadGroup(backendId + "_download")

  /**
   * Start downloading the given entry with the given timeout
   * @param timeoutMs operations timeout in millis, 0 stands for none
   */
  def start(de: DownloadEntry, timeoutMs: Int): Unit = de.status match {
    case s if s.canBeStarted => startInner(de, timeoutMs)
    case _                   => // NOOP
  }

  def startInner(de: DownloadEntry, timeoutMs: Int): Unit

  /**
   * Restart downloading the given entry with the given timeout
   * @param timeoutMs operations timeout in millis, 0 stands for none
   */
  def restart(de: DownloadEntry, timeoutMs: Int): Unit = {
    require(de.status != Status.Running, "Can't restart an ongoing download!")
    de.fileOption foreach (_.delete())
    addLogAndFire(de, LogEntry.info("Restarting download from the beginning"))
    de.sections.clear()
    de.supportsResumingOption = None
    eventMgr.fireDetailsChanged(de)
    changeStatusAndFire(de, Status.Stopped)
    start(de, timeoutMs)
  }

  def stop(de: DownloadEntry): Unit = de.status match {
    case s if s.canBeStopped => stopInner(de)
    case _                   => // NOOP
  }

  def stopInner(de: DownloadEntry): Unit

  //
  // Helpers
  //

  protected def eventMgr: EventManager

  protected def transferMgr: TransferManager

  /**
   * Should be invoked when download is complete.
   * Verifies the checksum hash (if any) and advances status to either Complete or Error.
   */
  protected def checkIntegrityAndComplete(de: DownloadEntry): Unit = {
    val passed = de.checksumOption match {
      case Some(checksum) =>
        addLogAndFire(de, LogEntry.info("Verifying checksum"))
        Checksums.check(checksum, de.fileOption.get)
      case None =>
        true
    }
    if (passed) {
      changeStatusAndFire(de, Status.Complete)
      addLogAndFire(de, LogEntry.info("Download complete"))
    } else {
      changeStatusAndFire(de, Status.Error)
      addLogAndFire(de, LogEntry.error("File integrity is violated, checksum doesn't match!"))
    }
  }

  protected def addLogAndFire(de: DownloadEntry, logEntry: LogEntry): Unit = {
    de.addDownloadLogEntry(logEntry)
    eventMgr.fireLogged(de, logEntry)
  }

  protected def changeStatusAndFire(de: DownloadEntry, newStatus: Status): Unit = {
    val prevStatus = de.status
    de.status = newStatus
    eventMgr.fireStatusChanged(de, prevStatus)
  }
}
