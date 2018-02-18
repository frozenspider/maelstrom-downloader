package org.fs.mael.core.event

import java.util.UUID

import org.fs.mael.core.controller.LogEntry
import org.fs.mael.core.controller.view.DownloadDetailsView

/**
 * UI event subscriber, interested in changes which may
 * affect the download presentation.
 *
 * @author FS
 */
trait UiSubscriber extends EventSubscriber {
  def added(dd: DownloadDetailsView): Unit

  def removed(ddId: UUID): Unit

  def error(dd: DownloadDetailsView): Unit

  /**
   * Download progress changed.
   *
   * Note that these events will be fired much more often than UI wish to process.
   */
  def progress(dd: DownloadDetailsView): Unit

  def logged(dd: DownloadDetailsView, entry: LogEntry): Unit
}
