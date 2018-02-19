package org.fs.mael.core.event

import org.fs.mael.core.entry.DownloadEntry

/**
 * Backend event subscriber, interested in changes which may
 * affect the ongoing download process.
 *
 * @author FS
 */
trait BackendSubscriber extends EventSubscriber {
  /** Download entry configuration changed */
  def configChanged(de: DownloadEntry): Unit
}
