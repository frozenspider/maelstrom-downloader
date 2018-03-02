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
  def fired(event: EventForUi): Unit
}
