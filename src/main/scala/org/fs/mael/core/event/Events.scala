package org.fs.mael.core.event

import org.fs.mael.core.Status
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.entry.LogEntry

sealed abstract class PriorityEvent(val priority: Int) {
  def msg: String
  var order: Long = -1

  override def toString(): String = {
    val fullName = this.getClass.getName
    val lastSepIdx = fullName.lastIndexWhere(c => c == '.' || c == '$')
    s"${fullName.drop(lastSepIdx + 1)}($priority, $order, $msg)"
  }
}

sealed trait EventForUi

sealed trait EventForBackend

object Events {

  //
  // UI events
  //

  /** Download entry configuration changed */
  case class ConfigChanged(
    de: DownloadEntry[_ <: BackendSpecificEntryData]
  ) extends PriorityEvent(Int.MaxValue) with EventForBackend {
    override def msg = "Config changed for " + de.uri
  }

  //
  // List manager events
  //

  /**
   * New download added to list.
   *
   * Should be fired by download list manager.
   */
  case class Added(de: DownloadEntryView) extends PriorityEvent(100) with EventForUi {
    override def msg = "Added " + de.uri
  }

  /**
   * Download removed from list.
   *
   * Should be fired by download list manager.
   */
  case class Removed(de: DownloadEntryView) extends PriorityEvent(100) with EventForUi {
    override def msg = "Removed " + de.uri
  }

  //
  // Backend events
  //

  /**
   * Download status changed.
   *
   * Should be fired by backend.
   */
  case class StatusChanged(de: DownloadEntryView, prevStatus: Status) extends PriorityEvent(100) with EventForUi {
    override def msg = "Status of " + de.uri + " changed from " + prevStatus + " to " + de.status
  }

  /**
   * Any displayed download detail (other than download progress) changed.
   *
   * Should be fired by backend.
   */
  case class DetailsChanged(de: DownloadEntryView) extends PriorityEvent(50) with EventForUi {
    override def msg = "Details changed for " + de.uri
  }

  /**
   * New entry added to download log.
   *
   * Should be fired by backend.
   */
  case class Logged(de: DownloadEntryView, entry: LogEntry) extends PriorityEvent(20) with EventForUi {
    override def msg = "Log entry added for " + de.uri
  }

  /**
   * Download progress changed.
   *
   * Should be fired by backend.
   *
   * (Note that these events will be fired much more often than UI would wish to process.)
   */
  case class Progress(de: DownloadEntryView) extends PriorityEvent(Int.MinValue) with EventForUi {
    override def msg = "Progress for " + de.uri
  }

}
