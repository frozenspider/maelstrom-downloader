package org.fs.mael.core.event

import org.fs.mael.core.Status
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.Events._

trait EventManager {

  //
  // Client methods: subscription
  //

  def subscribe(subscriber: EventSubscriber): Unit

  def unsubscribe(id: String): Unit

  //
  // Client methods: events firing
  //

  def fire(event: PriorityEvent): Unit

  /** Download entry configuration changed */
  def fireConfigChanged(de: DownloadEntry[_ <: BackendSpecificEntryData]): Unit = {
    fire(ConfigChanged(de))
  }

  def fireAdded(de: DownloadEntryView): Unit = {
    fire(Added(de))
  }

  def fireRemoved(de: DownloadEntryView): Unit = {
    fire(Removed(de))
  }

  def fireStatusChanged(de: DownloadEntryView, prevStatus: Status): Unit = {
    fire(StatusChanged(de, prevStatus))
  }

  /** Any displayed download detail (other than download progress) changed */
  def fireDetailsChanged(de: DownloadEntryView): Unit = {
    fire(DetailsChanged(de))
  }

  def fireLogged(de: DownloadEntryView, entry: LogEntry): Unit = {
    fire(Logged(de, entry))
  }

  /** Download progress changed */
  def fireProgress(de: DownloadEntryView): Unit = {
    fire(Progress(de))
  }
}
