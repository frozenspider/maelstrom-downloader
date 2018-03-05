package org.fs.mael.core.event

/**
 * UI event subscriber, interested in changes which may
 * affect the download presentation.
 *
 * @author FS
 */
trait UiSubscriber extends EventSubscriber {
  override type EventType = EventForUi
  override def fired(event: EventForUi): Unit
}
