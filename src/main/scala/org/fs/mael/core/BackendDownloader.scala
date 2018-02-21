package org.fs.mael.core

import org.fs.mael.core.entry.DownloadEntry

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
}
