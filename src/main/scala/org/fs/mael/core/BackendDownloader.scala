package org.fs.mael.core

import org.fs.mael.core.entry.DownloadEntry

trait BackendDownloader[DE <: DownloadEntry] {
  import Status._

  def start(de: DE): Unit = de.status match {
    case Stopped | Error    => startInner(de)
    case Complete | Running => // NOOP
  }

  def startInner(de: DE): Unit

  def stop(de: DE): Unit = de.status match {
    case Running                    => stopInner(de)
    case Complete | Stopped | Error => // NOOP
  }

  def stopInner(de: DE): Unit
}
