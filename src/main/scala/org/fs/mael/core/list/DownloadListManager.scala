package org.fs.mael.core.list

import java.util.UUID

import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventManager

object DownloadListManager {
  private var entries: Set[DownloadEntry] = Set.empty

  /** Called initially upon application start, no event is fired */
  def init(entries: Iterable[DownloadEntry]): Unit = {
    this.synchronized {
      this.entries = entries.toSet
    }
  }

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
    this.synchronized {
      entries
    }
  }
}
