package org.fs.mael.core.event

import java.util.UUID

import org.fs.mael.core.controller.LogEntry
import org.fs.mael.core.controller.Status
import org.fs.mael.core.controller.entry.DownloadDetailsView

/**
 * UI event subscriber, interested in changes which may
 * affect the download presentation.
 *
 * @author FS
 */
trait UiSubscriber extends EventSubscriber {

  //
  // List manager events
  //

  /**
   * New download added to list.
   *
   * Should be fired by download list manager.
   */
  def added(dd: DownloadDetailsView): Unit

  /**
   * Download removed from list.
   *
   * Should be fired by download list manager.
   */
  def removed(ddId: UUID): Unit

  //
  // Backend events
  //

  /**
   * Download status changed.
   *
   * Should be fired by backend.
   */
  def statusChanged(dd: DownloadDetailsView, s: Status): Unit

  /**
   * Download progress changed.
   *
   * Should be fired by backend.
   *
   * (Note that these events will be fired much more often than UI would wish to process.)
   */
  def progress(dd: DownloadDetailsView): Unit

  /**
   * Any displayed download detail (other than download progress) changed.
   *
   * Should be fired by backend.
   */
  def details(dd: DownloadDetailsView): Unit

  /**
   * New entry added to download log.
   *
   * Should be fired by backend.
   */
  def logged(dd: DownloadDetailsView, entry: LogEntry): Unit
}
