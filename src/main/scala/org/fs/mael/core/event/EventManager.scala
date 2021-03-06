package org.fs.mael.core.event

import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntry
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
  def fireConfigChanged(de: DownloadEntry): Unit = {
    fire(ConfigChanged(de))
  }

  def fireSelectionChanged(des: Seq[DownloadEntry]): Unit = {
    fire(SelectionChanged(des))
  }

  def fireAdded(de: DownloadEntry): Unit = {
    fire(Added(de))
  }

  def fireRemoved(de: DownloadEntry): Unit = {
    fire(Removed(de))
  }

  def fireStatusChanged(de: DownloadEntry, prevStatus: Status): Unit = {
    fire(StatusChanged(de, prevStatus))
  }

  /** Any displayed download detail (other than download progress) changed */
  def fireDetailsChanged(de: DownloadEntry): Unit = {
    fire(DetailsChanged(de))
  }

  def fireLogged(de: DownloadEntry, entry: LogEntry): Unit = {
    fire(Logged(de, entry))
  }

  /** Download progress changed */
  def fireProgress(de: DownloadEntry): Unit = {
    fire(Progress(de))
  }

  /** Download speed changed */
  def fireSpeedEta(de: DownloadEntry, speedOption: Option[Long], etaSecondsOption: Option[Long]): Unit = {
    fire(SpeedEta(de, speedOption, etaSecondsOption))
  }
}
