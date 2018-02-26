package org.fs.mael.core.list

import org.fs.mael.core.Status
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventManager

object DownloadListManager {
  private var entries: Set[DownloadEntry[_]] = Set.empty

  /** Called initially upon application start, no event is fired */
  def init(entries: Iterable[DownloadEntry[_ <: BackendSpecificEntryData]]): Unit = {
    this.synchronized {
      this.entries = entries.collect {
        case de if de.status == Status.Running =>
          // Note: mutation! Avoid?
          de.status = Status.Stopped
          de
        case de =>
          de
      }.toSet
    }
  }

  /** Add a new entry to a download list, firing an event */
  def add(de: DownloadEntry[_]): Unit = {
    this.synchronized {
      entries += de
      EventManager.fireAdded(de)
    }
  }

  /** Remove an existing entry from a download list, firing an event */
  def remove(de: DownloadEntry[_]): Unit = {
    this.synchronized {
      entries -= de
      EventManager.fireRemoved(de)
    }
  }

  def list(): Set[DownloadEntry[_]] = {
    this.synchronized {
      entries
    }
  }
}
