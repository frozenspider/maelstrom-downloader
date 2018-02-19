package org.fs.mael.core.event

import java.util.UUID

import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.entry.LogEntry

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
  def added(de: DownloadEntryView): Unit

  /**
   * Download removed from list.
   *
   * Should be fired by download list manager.
   */
  def removed(de: DownloadEntryView): Unit

  //
  // Backend events
  //

  /**
   * Download status changed.
   *
   * Should be fired by backend.
   */
  def statusChanged(de: DownloadEntryView, s: Status): Unit

  /**
   * Download progress changed.
   *
   * Should be fired by backend.
   *
   * (Note that these events will be fired much more often than UI would wish to process.)
   */
  def progress(de: DownloadEntryView): Unit

  /**
   * Any displayed download detail (other than download progress) changed.
   *
   * Should be fired by backend.
   */
  def details(de: DownloadEntryView): Unit

  /**
   * New entry added to download log.
   *
   * Should be fired by backend.
   */
  def logged(de: DownloadEntryView, entry: LogEntry): Unit
}
