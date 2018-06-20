package org.fs.mael.core.speed

import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventForUi
import org.fs.mael.core.event.Events._
import org.fs.mael.core.event.UiSubscriber

/**
 * Keeps track and tracks the download speed and ETA.
 * Fires `SpeedEta` events.
 *
 * @author FS
 */
trait SpeedTracker extends UiSubscriber {
  override def fired(event: EventForUi): Unit = event match {
    case StatusChanged(de, _) => reset(de)
    case Progress(de)         => update(de)
    case _                    => // NOOP
  }

  def reset(de: DownloadEntry): Unit

  def update(de: DownloadEntry): Unit
}
