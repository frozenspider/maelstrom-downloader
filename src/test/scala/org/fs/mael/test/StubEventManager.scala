package org.fs.mael.test

import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.EventSubscriber
import org.fs.mael.core.event.PriorityEvent

/** Event manager that accumulates events */
class StubEventManager extends EventManager {
  var events: IndexedSeq[PriorityEvent] = IndexedSeq.empty

  override def subscribe(subscriber: EventSubscriber): Unit = { /* NOOP */ }

  override def unsubscribe(id: String): Unit = { /* NOOP */ }

  override def fire(event: PriorityEvent): Unit = {
    events = events :+ event
  }
}
