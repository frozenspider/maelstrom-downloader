package org.fs.mael.test

import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.EventSubscriber
import org.fs.mael.core.event.PriorityEvent

/** NOOP event manager */
class StubEventManager extends EventManager {
  def subscribe(subscriber: EventSubscriber): Unit = {}

  def unsubscribe(id: String): Unit = {}

  def fire(event: PriorityEvent): Unit = {}
}
