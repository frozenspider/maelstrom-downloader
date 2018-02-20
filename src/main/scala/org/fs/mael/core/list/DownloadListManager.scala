package org.fs.mael.core.list

import java.util.UUID

import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventManager

/*
 * TODO:
 * Persist
 */
object DownloadListManager {
  private var entries: Set[DownloadEntry] = Set.empty

  /** Add a new entry to a download list, firing an event */
  def add(de: DownloadEntry): Unit = {
    this.synchronized {
      entries += de
      EventManager.fireAdded(de)
    }
  }

  /** Remove an existing entry from a download list, firing an event */
  def remove(de: DownloadEntry): Unit = {
    this.synchronized {
      entries -= de
      EventManager.fireRemoved(de)
    }
  }

  def list(): Set[DownloadEntry] = {
    entries
  }
}
