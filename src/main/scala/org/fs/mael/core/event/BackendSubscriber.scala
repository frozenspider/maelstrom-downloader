package org.fs.mael.core.event

/**
 * Backend event subscriber, interested in changes which may
 * affect the ongoing download process.
 *
 * @author FS
 */
trait BackendSubscriber extends EventSubscriber {
  override type EventType = EventForBackend
  override def fired(event: EventForBackend): Unit
}
